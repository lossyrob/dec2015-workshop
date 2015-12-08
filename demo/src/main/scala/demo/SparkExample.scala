package demo

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.proj4._

import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.slippy._
import geotrellis.spark.ingest._
import geotrellis.spark.reproject._
import geotrellis.spark.tiling._
import geotrellis.spark.render._

import geotrellis.vector._

import org.apache.spark._
import org.apache.spark.rdd._

object SparkExample {
  val inputPath = fullPath("data/landsat-tiles")
  val outputPath = fullPath("data/tiles")

  def main(args: Array[String]): Unit = {
    val conf =
      new SparkConf()
        .setMaster("local[*]")
        .setAppName("Spark Example")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    val sc = new SparkContext(conf)

    try {
      run(sc)
      println("Hit enter to exit.")
      readLine()
    } finally {
      sc.stop()
    }
  }

  def fullPath(path: String) = new java.io.File(path).getAbsolutePath

  def run(implicit sc: SparkContext) = {
    val layoutScheme = ZoomedLayoutScheme(WebMercator)

    val inputTiles = sc.hadoopMultiBandGeoTiffRDD(inputPath)
    val (zoom, rasterMetaData) =
      RasterMetaData.fromRdd(inputTiles, WebMercator, layoutScheme)(_.projectedExtent.extent)
    val tiled: RDD[(SpatialKey, MultiBandTile)] = Tiler(inputTiles, rasterMetaData, Bilinear)

    val writeOp =
      Pyramid.upLevels(new MultiBandRasterRDD(tiled, rasterMetaData), layoutScheme, zoom) { (rdd, z) =>
        val md = rdd.metaData

        val writer = new HadoopSlippyTileWriter[MultiBandTile](outputPath, "tif")({ (key, tile) =>
          val extent = md.mapTransform(key)
          MultiBandGeoTiff(tile, extent, WebMercator).toByteArray
        })
        new MultiBandRasterRDD(writer.setupWrite(z, rdd), md)
      }

    writeOp.foreach { x => }
  }
}
