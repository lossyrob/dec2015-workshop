package sampleapp

import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.io.json._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.op.zonal.summary._
import geotrellis.raster.render._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.index._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.json._
import geotrellis.spark.op.zonal.summary._
import geotrellis.spark.op.stats._

import geotrellis.vector._
import geotrellis.vector.io.json._
import geotrellis.vector.reproject._
import geotrellis.proj4._

import geotrellis.spark.io.accumulo._
import org.apache.accumulo.core.client.security.tokens.PasswordToken

import akka.actor._
import akka.io.IO
import spray.can.Http
import spray.routing.{HttpService, RequestContext}
import spray.routing.directives.CachingDirectives
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.{ AllOrigins, MediaTypes }
import spray.http.{ HttpMethods, HttpMethod, HttpResponse, AllOrigins }
import spray.routing._

import scala.concurrent._
import org.apache.spark._
import org.apache.spark.rdd._
import com.github.nscala_time.time.Imports._


object TimeSeriesExample {
  val tilesPath = new java.io.File("data/tiles-wm").getAbsolutePath

  def main(args: Array[String]): Unit = {
    implicit val system = akka.actor.ActorSystem("sampleapp-system")

    val conf =
      new SparkConf()
        .setAppName("SampleApp Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    val (reader, attributeStore) =
      if(args(0) == "local") {
        val localCatalog = args(1)
        val r = HadoopLayerReader[SpaceTimeKey, Tile, RasterRDD](localCatalog)
        (r, r.attributeStore)
      } else if(args(0) == "s3"){
        val bucket = "ksat-test-1"
        val prefix = "catalog"

        val r = S3LayerReader[SpaceTimeKey, Tile, RasterRDD](bucket, prefix)
        (r, r.attributeStore)
      } else {
        val instanceName = "geotrellis-accumulo-cluster"
        val zooKeeper = "zookeeper.service.ksat-demo.internal"
        val user = "root"
        val password = new PasswordToken("secret")
        val instance = AccumuloInstance(instanceName, zooKeeper, user, password)
        val reader = AccumuloLayerReader[SpaceTimeKey, Tile, RasterRDD](instance)
        (reader, reader.attributeStore)
      }

    // create and start our service actor
    val service =
      system.actorOf(Props(classOf[TimeSeriesServiceActor], reader, attributeStore, sc), "sampleapp")

    // start a new HTTP server on port 8081 with our service actor as the handler
    IO(Http) ! Http.Bind(service, "localhost", 8081)
  }
}

class TimeSeriesServiceActor(
  reader: FilteringLayerReader[LayerId, SpaceTimeKey, RasterRDD[SpaceTimeKey]], 
  attributeStore: AttributeStore[JsonFormat],
  sc: SparkContext) extends Actor with HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global

  val layerName = "rainfall"
  val maxZoom = 4

  def actorRefFactory = context
  def receive = runRoute(root)

  def root = zonalRoutes

  def zonalRoutes = cors {
    (pathPrefix("zonal") & (post) ) {
      import DefaultJsonProtocol._ 
      import org.apache.spark.SparkContext._        
      
      val layer = LayerId("rainfall", 4)
      
      entity(as[Polygon]) { poly =>
        val (_, metadata, _, _, _) =
          attributeStore.readLayerAttributes[HadoopLayerHeader, RasterMetaData, KeyBounds[SpaceTimeKey], KeyIndex[SpaceTimeKey], Unit](layer)

        val polygon = poly.reproject(LatLng, WebMercator)
        val bounds = metadata.mapTransform(polygon.envelope)
        val tiles: RasterRDD[SpaceTimeKey] = 
          reader.query(layer)
            .where(Intersects(bounds))
            .toRDD

        path("min") {
          complete {
            statsReponse(layer.name, "min",
              tiles
                .mapKeys { key => key.updateTemporalComponent(key.temporalKey.time.withDayOfMonth(1).withHourOfDay(0)) }
                .mapTiles { tile => tile.convert(TypeInt).map { z => if(z == 0) NODATA else z } }
                .averageByKey
                .zonalSummaryByKey(polygon, Double.MaxValue, MinDouble, { k:SpaceTimeKey => k.temporalComponent.time })
                .collect
                .sortBy(_._1) )
          } 
        } ~
        path("min-no-zeros") {
          complete {
            statsReponse(layer.name, "min",
              tiles
                .mapKeys { key => key.updateTemporalComponent(key.temporalKey.time.withDayOfMonth(1).withHourOfDay(0)) }
                .mapTiles { tile => tile.convert(TypeInt).map { z => if(z == 0) NODATA else z } }
                .averageByKey
                .zonalSummaryByKey(polygon, Double.MaxValue, MinDouble, { k: SpaceTimeKey => k.temporalComponent.time })
                .collect
                .sortBy(_._1) )
          } 
        } ~
        path("max") { 
          complete {    
            statsReponse(layer.name, "max",
              tiles
                .mapKeys { key => key.updateTemporalComponent(key.temporalKey.time.withDayOfMonth(1).withHourOfDay(0)) }
                .averageByKey
                .zonalSummaryByKey(polygon, Double.MinValue, MaxDouble, { k: SpaceTimeKey => k.temporalComponent.time })
                .collect
                .sortBy(_._1) )
          } 
        } ~
        path("mean") { 
          complete {    
            statsReponse(layer.name, "mean",
              tiles
                .mapKeys { key => key.updateTemporalComponent(key.temporalKey.time.withDayOfMonth(1).withHourOfDay(0)) }
                .averageByKey
                .zonalSummaryByKey(polygon, MeanResult(0.0, 0L), Mean, { k:SpaceTimeKey => k.temporalComponent.time })
                .mapValues(_.mean)
                .collect
                .sortBy(_._1) )
          } 
        }
      }
    }
  }

  val corsHeaders = List(`Access-Control-Allow-Origin`(AllOrigins),
    `Access-Control-Allow-Methods`(GET, POST, OPTIONS, DELETE),
    `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent, Access-Control-Request-Method, Access-Control-Request-Headers"))

  def cors: Directive0 = {
    val rh = implicitly[RejectionHandler]
    respondWithHeaders(corsHeaders) & handleRejections(rh)
  }

  def statsReponse(model: String, name: String, data: Seq[(DateTime, Double)]) =  
    JsArray(JsObject(
      "model" -> JsString(model),
      "data" -> JsArray(
        data.map { case (date, value) =>
          JsObject(
            "time" -> JsString(date.toString),
            name -> JsNumber(value)
          )
        }: _*)
    ))
}

