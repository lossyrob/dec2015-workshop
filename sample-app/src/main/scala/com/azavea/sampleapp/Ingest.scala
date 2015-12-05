package sampleapp

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.proj4._
import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.reproject._
import geotrellis.spark.io.index._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.tiling._

import org.apache.spark._
import org.apache.spark.rdd._
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.conf.Configuration

import geotrellis.spark.io.accumulo._
import org.apache.accumulo.core.client.security.tokens.PasswordToken

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

object DateTimeIndexValue extends Function1[DateTime, Int] with Serializable {
  val startTime = new DateTime(2000, 1, 1, 0, 0)
  def apply(dt: DateTime): Int =
    Days.daysBetween(startTime, dt).getDays
}

object Ingest {
  def main(args: Array[String]): Unit = {
    val conf =
      new SparkConf()
        .setAppName("SampleApp Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    if(args(0) == "local") {
      val inputDir = args(1)
      val localCatalog = args(2)
      callLocal(inputDir, localCatalog)
      return
    }

    val bucket = "ksat-test-1"
    val prefix = "rainfall-wm"
    val splitSize = args(1).toInt

    try {
      val layoutScheme = ZoomedLayoutScheme(WebMercator)
      val sourceTiles = loadSourceTiles(bucket, prefix, splitSize)

      val (zoom, rasterMetaData) =
        RasterMetaData.fromRdd(sourceTiles, WebMercator, layoutScheme)(_.projectedExtent.extent)
      val tiled: RDD[(SpaceTimeKey, Tile)] = Tiler(sourceTiles, rasterMetaData, Bilinear)

      val layerId = LayerId("rainfall", zoom)
      val rasterRDD = new RasterRDD(tiled, rasterMetaData)

      if(args(0) == "s3") {
        val targetPrefix = "catalog"

        saveToS3(layerId, rasterRDD, layoutScheme, bucket, targetPrefix)
      } else {
        saveToAccumulo(layerId, rasterRDD, layoutScheme)
      }
    } finally {
      sc.stop()
    }
  }

  def loadSourceTiles(bucket: String, prefix: String, splitSize: Int)(implicit sc: SparkContext): RDD[(SpaceTimeInputKey, Tile)] = {
    val job = Job.getInstance(sc.hadoopConfiguration)
    S3InputFormat.setBucket(job, bucket)
    S3InputFormat.setPrefix(job, prefix)
    S3InputFormat.setRegion(job, "eu-central-1")
    S3InputFormat.setMaxKeys(job, splitSize)

    val conf = job.getConfiguration

    sc.newAPIHadoopRDD(conf, classOf[RainfallInputFormat], classOf[SpaceTimeInputKey], classOf[Tile])
  }

  def saveToS3(layerId: LayerId, rasterRDD: RasterRDD[SpaceTimeKey], layoutScheme: LayoutScheme, bucket: String, prefix: String): Unit = {
    val writer = S3LayerWriter[SpaceTimeKey, Tile, RasterRDD](bucket, prefix, ZCurveKeyIndexMethod.byYear)
    val LayerId(name, zoom) = layerId

    Pyramid.upLevels(rasterRDD, layoutScheme, zoom) { (rdd, z) =>
      writer.write(LayerId(name, z), rdd)
      rdd
    }
  }

  def saveToAccumulo(layerId: LayerId, rasterRDD: RasterRDD[SpaceTimeKey], layoutScheme: LayoutScheme): Unit = {
    val instanceName = "geotrellis-accumulo-cluster"
    val zooKeeper = "zookeeper.service.ksat-demo.internal"
    val user = "root"
    val password = new PasswordToken("secret")
    val instance = AccumuloInstance(instanceName, zooKeeper, user, password)
    val writer = AccumuloLayerWriter[SpaceTimeKey, Tile, RasterRDD](instance, "tiles", ZCurveKeyIndexMethod.byYear)
    val LayerId(name, zoom) = layerId

    Pyramid.upLevels(rasterRDD, layoutScheme, zoom) { (rdd, z) =>
      writer.write(LayerId(name, z), rdd)
      rdd
    }
  }

  def callLocal(inputDir: String, localCatalog: String)(implicit sc: SparkContext) = {
    val layoutScheme = ZoomedLayoutScheme(WebMercator)

    val bucket = "ksat-test-1"
    val prefix = "rainfall-wm"
    val targetPrefix = "catalog"

    try {
      val sourceTiles =
        sc.newAPIHadoopRDD(
          sc.hadoopConfiguration.withInputDirectory(inputDir,sc.defaultTiffExtensions),
          classOf[RainfallHadoopInputFormat], 
          classOf[SpaceTimeInputKey],
          classOf[Tile]
        ).repartition(5)

      val (zoom, rasterMetaData) =
        RasterMetaData.fromRdd(sourceTiles, WebMercator, layoutScheme)(_.projectedExtent.extent)

      val tiled = Tiler(sourceTiles, rasterMetaData, Bilinear)

      val layerId = LayerId("rainfall", zoom)
      val rasterRDD = new RasterRDD(tiled, rasterMetaData)

      saveToLocal(layerId, rasterRDD, layoutScheme, localCatalog)
    } finally {
      sc.stop()
    }
  }

  def saveToLocal(layerId: LayerId, rasterRDD: RasterRDD[SpaceTimeKey], layoutScheme: LayoutScheme, localCatalog: String): Unit = {
    val writer = HadoopLayerWriter[SpaceTimeKey, Tile, RasterRDD](localCatalog, ZCurveKeyIndexMethod.by(DateTimeIndexValue))
    val LayerId(name, zoom) = layerId

    Pyramid.upLevels(rasterRDD, layoutScheme, zoom, zoom) { (rdd, z) =>
      writer.write(LayerId(name, z), rdd)
      rdd
    }
  }
}
