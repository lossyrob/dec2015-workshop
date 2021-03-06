package demo

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.spark.io.slippy._

import geotrellis.vector._

import akka.actor._
import akka.io.IO
import spray.can.Http
import spray.routing.{HttpService, RequestContext}
import spray.routing.directives.CachingDirectives
import spray.http.MediaTypes
import scala.concurrent._
import com.typesafe.config.ConfigFactory

object SprayExample {
  val tilesPath = new java.io.File("data/tiles").getAbsolutePath

  val reader = new FileSlippyTileReader[MultiBandTile](tilesPath)({ (key, bytes) =>
    MultiBandGeoTiff(bytes).tile
  })

  def main(args: Array[String]): Unit = {
    implicit val system = akka.actor.ActorSystem("demo-system")

    // create and start our service actor
    val service =
      system.actorOf(Props(classOf[SampleServiceActor]), "demo")

    // start a new HTTP server on port 8080 with our service actor as the handler
    IO(Http) ! Http.Bind(service, "localhost", 8080)
  }
}

class SampleServiceActor extends Actor with HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global

  def actorRefFactory = context
  def receive = runRoute(root)

  val colorBreaks = 
    ColorBreaks.fromStringDouble(ConfigFactory.load().getString("demo.colorbreaks")).get

  def root =
    pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
      respondWithMediaType(MediaTypes.`image/png`) {
        complete {
          future {

            val tile = SprayExample.reader.read(zoom, x, y)

            val ndvi =
              tile.convert(TypeDouble).combineDouble(0, 1) { (r, ir) =>
                if(isData(r) && isData(ir)) {
                  (ir - r) / (ir + r)
                } else {
                  Double.NaN
                }
              }

            ndvi.renderPng(colorBreaks).bytes
          }
        }
      }
    }
}
