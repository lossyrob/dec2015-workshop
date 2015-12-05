package sampleapp

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.proj4._
import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.io.index._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.tiling._

import org.apache.spark._
import org.apache.spark.rdd._
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.conf.Configuration

import com.github.nscala_time.time.Imports._

object FindMinMaxTime {
  def main(args: Array[String]): Unit = {
    val conf =
      new SparkConf()
        .setAppName("SampleApp Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    if(args.length > 0) {
      val inputDir = args(0)
      callLocal(inputDir)
      return
    }

    val layoutScheme = ZoomedLayoutScheme(WebMercator)

    val bucket = "ksat-test-1"
    val prefix = "rainfall-wm"

    val order = implicitly[Ordering[DateTime]]

    type MinMax = (DateTime, DateTime)

    try {
      val sourceTiles = loadSourceTiles(bucket, prefix)

      val seqOp: (Option[MinMax], DateTime) => Option[MinMax] =
        { (acc, dt) =>
          acc match {
            case Some((min, max)) =>
              Some((order.min(min, dt), order.max(max, dt)))
            case None =>
              Some((dt, dt))
          }
        }

      val combineOp: (Option[MinMax], Option[MinMax]) => Option[MinMax] =
        { (acc1, acc2) =>
          acc1 match {
            case Some((min1, max1)) =>
              acc2.map { case(min2, max2) =>
                (order.min(min1, min2), order.max(max1, max2))
              }
            case None =>
              acc2
          }
        }

      val sumAcc = sc.accumulator(0)

      val (min, max) =
        sourceTiles
          .map { case (key, _) => 
            sumAcc += 1
            key.time
          }
          .aggregate[Option[MinMax]](None)(seqOp, combineOp)
          .get

      println(s"MIN DATE: $min")
      println(s"MAX DATE: $max")
      println(s"COUNT: ${sumAcc.value}")
    } finally {
      sc.stop()
    }
  }

  def loadSourceTiles(bucket: String, prefix: String)(implicit sc: SparkContext): RDD[(SpaceTimeInputKey, Tile)] = {
    val job = Job.getInstance(sc.hadoopConfiguration)
    S3InputFormat.setBucket(job, bucket)
    S3InputFormat.setPrefix(job, prefix)
    S3InputFormat.setRegion(job, "eu-central-1")
    S3InputFormat.setMaxKeys(job, 20)

    val conf = job.getConfiguration

    sc.newAPIHadoopRDD(conf, classOf[RainfallInputFormat], classOf[SpaceTimeInputKey], classOf[Tile])
  }

  def callLocal(inputDir: String)(implicit sc: SparkContext) = {
    val layoutScheme = ZoomedLayoutScheme(WebMercator)

    val order = implicitly[Ordering[DateTime]]

    type MinMax = (DateTime, DateTime)

    try {
      val sourceTiles = 
        sc.newAPIHadoopRDD(
          sc.hadoopConfiguration.withInputDirectory(inputDir,sc.defaultTiffExtensions),
          classOf[RainfallHadoopInputFormat], 
          classOf[SpaceTimeInputKey], 
          classOf[Tile]
        )

      val seqOp: (Option[MinMax], DateTime) => Option[MinMax] =
        { (acc, dt) =>
          acc match {
            case Some((min, max)) =>
              Some((order.min(min, dt), order.max(max, dt)))
            case None =>
              Some((dt, dt))
          }
        }

      val combineOp: (Option[MinMax], Option[MinMax]) => Option[MinMax] =
        { (acc1, acc2) =>
          acc1 match {
            case Some((min1, max1)) =>
              acc2.map { case(min2, max2) =>
                (order.min(min1, min2), order.max(max1, max2))
              }
            case None =>
              acc2
          }
        }

      val sumAcc = sc.accumulator(0)

      val (min, max) =
        sourceTiles
          .map { case (key, _) => 
            sumAcc += 1
            key.time
          }
          .aggregate[Option[MinMax]](None)(seqOp, combineOp)
          .get

      println(s"MIN DATE: $min")
      println(s"MAX DATE: $max")
      println(s"COUNT: ${sumAcc.value}")
    } finally {
      sc.stop()
    }
  }
}
