package sampleapp

import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.hadoop.formats._
import geotrellis.spark.ingest._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.vector._
import org.apache.hadoop.mapreduce._
import org.apache.hadoop.mapreduce.lib.input._
import org.joda.time._
import org.joda.time.format._

class RainfallHadoopInputFormat extends FileInputFormat[SpaceTimeInputKey, Tile] {
  def read(bytes: Array[Byte], path: String): (SpaceTimeInputKey, Tile) = {
//    val DateString = """(\d\d\d\d)\.(\d\d)\.(\d\d)""".r.unanchored
    val DateString = """3IMERG\.(\d\d\d\d)(\d\d)(\d\d)""".r.unanchored
    val dateTime = 
      path match {
        case DateString(y, m, d) =>
          new DateTime(y.toInt, m.toInt, d.toInt, 12, 0, 0)
        case _ =>
          sys.error(s"Couldn't find Date in path $path")
      }
    val geoTiff = SingleBandGeoTiff(bytes)

    val ProjectedRaster(tile, extent, crs) = geoTiff.projectedRaster
    (SpaceTimeInputKey(extent, crs, dateTime), tile)
  }

  override def createRecordReader(split: InputSplit, context: TaskAttemptContext): RecordReader[SpaceTimeInputKey, Tile] = 
    new BinaryFileRecordReader({ bytes => read(bytes, split.asInstanceOf[FileSplit].getPath.toString) })
}
