package demo

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
import org.apache.avro.Schema

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

object ServerExample {
  val tilesPath = new java.io.File("data/tiles-wm").getAbsolutePath

  def main(args: Array[String]): Unit = {
    implicit val system = akka.actor.ActorSystem("demo-system")

    val conf =
      new SparkConf()
        .setAppName("Demo Server")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    val (reader, metadataReader) =
      if(args(0) == "local") {
        val localCatalog = args(1)
        val reader = HadoopLayerReader[SpaceTimeKey, Tile, RasterRDD](localCatalog)
        val metadataReader = 
          new Reader[LayerId, RasterMetaData] {
            def read(layer: LayerId) = {
              reader.attributeStore.readLayerAttributes[HadoopLayerHeader, RasterMetaData, KeyBounds[SpaceTimeKey], KeyIndex[SpaceTimeKey], Unit](layer)._2
            }
          }

        (reader, metadataReader)
      } else if(args(0) == "s3"){
        val bucket = "ksat-test-1"
        val prefix = "catalog"

        val reader = S3LayerReader[SpaceTimeKey, Tile, RasterRDD](bucket, prefix)
        val metadataReader = 
          new Reader[LayerId, RasterMetaData] {
            def read(layer: LayerId) = {
              reader.attributeStore.readLayerAttributes[S3LayerHeader, RasterMetaData, KeyBounds[SpaceTimeKey], KeyIndex[SpaceTimeKey], Schema](layer)._2
            }
          }

        (reader, metadataReader)
      } else {
        val instanceName = "geotrellis-accumulo-cluster"
        val zooKeeper = "zookeeper.service.ksat-demo.internal"
        val user = "root"
        val password = new PasswordToken("secret")
        val instance = AccumuloInstance(instanceName, zooKeeper, user, password)
        val reader = AccumuloLayerReader[SpaceTimeKey, Tile, RasterRDD](instance)
        val metadataReader = 
          new Reader[LayerId, RasterMetaData] {
            def read(layer: LayerId) = {
              reader.attributeStore.readLayerAttributes[AccumuloLayerHeader, RasterMetaData, KeyBounds[SpaceTimeKey], KeyIndex[SpaceTimeKey], Schema](layer)._2
            }
          }

        (reader, metadataReader)
      }

    // create and start our service actor
    val service =
      system.actorOf(Props(classOf[ServerExampleServiceActor], reader, metadataReader, sc), "demo")

    // start a new HTTP server on port 8088 with our service actor as the handler
    IO(Http) ! Http.Bind(service, "0.0.0.0", 8088)
  }
}

class ServerExampleServiceActor(
  reader: FilteringLayerReader[LayerId, SpaceTimeKey, RasterRDD[SpaceTimeKey]], 
  metadataReader: Reader[LayerId, RasterMetaData],
  sc: SparkContext) extends Actor with HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global

  val layer = LayerId("rainfall", 0)

  def actorRefFactory = context
  def receive = runRoute(root)

  def root = 
    path("ping") { complete { "pong\n" } } ~
    pathPrefix("total") { totalRoutes } ~
    pathPrefix("point") { pointRoutes } ~
    pathPrefix("region") { zonalRoutes } ~
    pathPrefix("zonal-timeseries") { zonalTimeSeriesRoutes }

  def totalRoutes = cors {

      path("max") {
        complete {
          val tiles: RasterRDD[SpaceTimeKey] =
            reader.query(layer)
              .toRDD

          zonalStatsReponse(
            layer.name, "max", 
            tiles
              .map { case (_, tile) => tile.findMinMax._2 }
              .max
          )
        }
      } ~
      path("mean") {
        complete {
          val tiles: RasterRDD[SpaceTimeKey] =
            reader.query(layer)
              .toRDD

          val (count, sum) =
            tiles
              .map { case (_, tile) =>  
                var sum = 0L
                var count = 0
                tile.foreach { z => 
                  if(isData(z)) {
                    sum += z 
                    count += 1
                  }
                }
                (sum, count)
              }
              .reduce { (acc1, acc2) => (acc1._1 + acc2._1, acc1._2 + acc2._2) }
          
          zonalStatsReponse(layer.name, "mean", count / sum.toDouble)
        }
      }
  }

  def pointRoutes = cors {
    pathPrefix(DoubleNumber / DoubleNumber) { (x, y) =>
      val point = Point(x, y).reproject(LatLng, WebMercator)

      val metadata =
        metadataReader.read(layer)

      val tiles: RasterRDD[SpaceTimeKey] =
        reader.query(layer)
          .where(Contains(point))
          .toRDD

      path("all") {
        complete {
          val p = point
          timeSeriesStatsReponse(layer.name, "raw",
            tiles
              .asRasters
              .map { case (key, raster) => (key.time, raster.getDouble(p)) }
              .collect
          )
        }
      } ~
      path("max") {
        complete {
          val p = point 
          timeSeriesStatsReponse(
            layer.name, "max", 
            tiles
              .asRasters
              .map { case (key, raster) => (key.time.withDayOfMonth(1).withHourOfDay(0), raster.getDouble(p)) }
              .reduceByKey(math.max)
              .collect
          )
        }
      } ~
      path("mean") {
        complete {
          val seqOp: ((Double, Int), Int) => (Double, Int) =
            { (acc, v) => if(isData(v)) { (acc._1 + v, acc._2 + 1) } else acc }

          val combineOp: ((Double, Int), (Double, Int)) => (Double, Int) =
            { (acc1, acc2) => (acc1._1 + acc2._1, acc1._2 + acc2._2) }

          timeSeriesStatsReponse(
            layer.name, "mean", 
            tiles
              .asRasters
              .map { case (key, raster) => (key.time.withDayOfMonth(1).withHourOfDay(0), raster.get(point)) }
              .aggregateByKey((0.0, 0))(seqOp, combineOp)
              .mapValues { case (sum, count) => sum / count.toDouble }
              .collect
          )
        }
      }
    }
  }

  def zonalRoutes = cors {
    post {
      import DefaultJsonProtocol._ 
      import org.apache.spark.SparkContext._        
      
      entity(as[Polygon]) { poly =>
        val metadata =
          metadataReader.read(layer)

        val polygon = poly.reproject(LatLng, WebMercator)
        val bounds = metadata.mapTransform(polygon.envelope)
        val tiles: RasterRDD[SpaceTimeKey] = 
          reader.query(layer)
            .where(Intersects(bounds))
            .toRDD

        path("max") { 
          complete {    
            zonalStatsReponse(
              layer.name, "max", tiles.zonalMax(polygon)
            )
          } 
        } ~
        path("mean") { 
          complete {    
            zonalStatsReponse(
              layer.name, "mean", tiles.zonalMean(polygon)
            )
          } 
        } ~
        path("mean-for-year" / IntNumber) { year =>
          val yearTiles: RasterRDD[SpaceTimeKey] =
            reader.query(layer)
              .where(Intersects(bounds))
              .where(Between(new DateTime(year, 1, 1, 0, 0), new DateTime(year, 12, 31, 0, 0)))
              .toRDD

          complete {    
            zonalStatsReponse(
              layer.name, "mean", yearTiles.zonalMean(polygon)
            )
          } 
        }
      }
    }
  }

  def zonalTimeSeriesRoutes = cors {
    post {
      import DefaultJsonProtocol._ 
      import org.apache.spark.SparkContext._        
      
      entity(as[Polygon]) { poly =>
        val metadata =
          metadataReader.read(layer)

        val polygon = poly.reproject(LatLng, WebMercator)
        val bounds = metadata.mapTransform(polygon.envelope)
        val tiles: RasterRDD[SpaceTimeKey] = 
          reader.query(layer)
            .where(Intersects(bounds))
            .toRDD

        path("max") {
          complete {    
            timeSeriesStatsReponse(layer.name, "max",
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
            timeSeriesStatsReponse(layer.name, "mean",
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

  def zonalStatsReponse(model: String, name: String, value: Double) =  
    JsArray(JsObject(
      "model" -> JsString(model),
      "data" -> 
        JsObject(
          name -> JsNumber(value)
        )
    ))

  def timeSeriesStatsReponse(model: String, name: String, data: Seq[(DateTime, Double)]) =  
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
