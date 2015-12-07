package demo

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._

object RasterExample {
  val maskedPath = "data/r-ir.tif"
  val ndviPath = "data/ndvi.png"

  def main(args: Array[String]): Unit = {
    // createMasked()
    createNDVI()
  }

  def createMasked(): Unit = {
    def bandPath(b: String) = s"data/landsat/LC80140322014139LGN00_${b}.TIF"

    val rGeoTiff = SingleBandGeoTiff(bandPath("B4"))
    val irGeoTiff = SingleBandGeoTiff(bandPath("B5"))
    val qaGeoTiff = SingleBandGeoTiff(bandPath("BQA"))

    val (rTile, irTile, qaTile) = (rGeoTiff.tile, irGeoTiff.tile, qaGeoTiff.tile)

    def maskClouds(tile: Tile): Tile =
      tile.combine(qaTile) { (v, qa) =>
        val isCloud = qa & 0x8000
        val isCirrus = qa & 0x2000
        if(isCloud > 0 || isCirrus > 0) { NODATA}
        else { v }
      }

    val rMasked = maskClouds(rTile)

    val irMasked = maskClouds(irTile)

    val mb = ArrayMultiBandTile(rMasked, irMasked).convert(TypeInt)
    MultiBandGeoTiff(mb, rGeoTiff.extent, rGeoTiff.crs).write(maskedPath)
  }

  def createNDVI(): Unit = {
    val ndvi = {
      val tile = MultiBandGeoTiff(maskedPath).convert(TypeDouble)

      tile.combineDouble(0, 1) { (r, ir) =>
        if(isData(r) && isData(ir)) { 
          (ir - r) / (ir + r) 
        } else { 
          Double.NaN 
        }
      }
    }

    val cb = ColorBreaks.fromStringDouble("0:ffffe5ff;0.1:f7fcb9ff;0.2:d9f0a3ff;0.3:addd8eff;0.4:78c679ff;0.5:41ab5dff;0.6:238443ff;0.7:006837ff;1:004529ff").get

    ndvi.renderPng(cb).write(ndviPath)
  }
}
