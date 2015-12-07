package demo

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.io.s3._
import geotrellis.vector.Extent
import org.apache.hadoop.mapreduce._
import org.apache.hadoop.mapreduce.lib.input._
import org.joda.time._
import org.joda.time.format._

class RainfallInputFormat extends S3InputFormat[SpaceTimeInputKey,Tile] {
  def createRecordReader(split: InputSplit, context: TaskAttemptContext) = {
    new S3RecordReader[SpaceTimeInputKey,Tile] {
      def read(key: String, bytes: Array[Byte]) = {
        val DateString = """3IMERG\.(\d\d\d\d)(\d\d)(\d\d)""".r.unanchored
        val dateTime =
          key match {
            case DateString(y, m, d) =>
              new DateTime(y.toInt, m.toInt, d.toInt, 12, 0, 0)
            case _ =>
              sys.error(s"Couldn't find Date for key $key")
          }

        val geoTiff = SingleBandGeoTiff(bytes)

        val ProjectedRaster(tile, extent, crs) = geoTiff.projectedRaster
        (SpaceTimeInputKey(extent, crs, dateTime), tile)        
      }
    }
  }
}

