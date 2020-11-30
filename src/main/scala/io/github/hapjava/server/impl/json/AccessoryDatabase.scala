package io.github.hapjava.server.impl.json

import java.util.concurrent.ConcurrentHashMap

import com.github.otah.hap.api._
import com.github.otah.hap.api.json.AccessoryJson
import com.github.otah.hap.api.server.HomeKitRoot
import com.typesafe.scalalogging.Logger
import io.github.hapjava.characteristics.{EventableCharacteristic, HomekitCharacteristicChangeCallback}
import io.github.hapjava.server.impl.connections.SubscriptionManager
import io.github.hapjava.server.impl.http.{HomekitClientConnection, HttpResponse}
import spray.json._

import scala.jdk.CollectionConverters._
import scala.concurrent._
import scala.util.Try

class AccessoryDatabase(
  root: HomeKitRoot, subscriptions: SubscriptionManager
)(implicit
  ec: ExecutionContext, formatter: JsonFormatter
) {
  import AccessoryDatabase._

  private val log = Logger[AccessoryDatabase]

  private def logCharacteristic(text: => String)(implicit ctx: ChContext): Unit =
    log.debug(s"[CH ${ctx.aid}.${ctx.iid}] $text")

  private object JsonProtocol extends DefaultJsonProtocol {
    implicit val format1 = jsonFormat4(SingleCharacteristicAdjustment)
    implicit val format2 = jsonFormat1(CharacteristicsPutRequest)
  }
  import JsonProtocol._

  private def toEventable(characteristic: LowLevelCharacteristic)(implicit ctx: ChContext) = {
    def convertNotifier(notifier: LowLevelNotifier): EventableCharacteristic = new EventableCharacteristic {

      private val subscriptions = new ConcurrentHashMap[Subscription, Unit]()

      override def subscribe(callback: HomekitCharacteristicChangeCallback): Unit = {
        logCharacteristic("Subscribing a new change callback")
        val subscription = notifier.subscribe { v =>
          Future(callback.changed(v))
        }

        subscriptions.put(subscription, ())
        logCharacteristic(s"Current number of subscriptions: ${subscriptions.size}")
      }

      override def unsubscribe(): Unit = {
        logCharacteristic("Unsubscribing change callbacks")
        subscriptions.keySet().asScala.toSet foreach { subscription: Subscription =>
          subscriptions.remove(subscription)
          subscription.unsubscribe()
        }
      }
    }

    characteristic.jsonValueNotifier map convertNotifier
  }

  private val characteristicsAsMap: Map[ChContext, (LowLevelCharacteristic, Option[EventableCharacteristic])] = {
    val flattenedTuples = root.accessories flatMap { acc =>
      acc.accessory.lowLevelServices flatMap (_.service.characteristics) map { ch =>
        implicit val ids = ChContext(acc.aid.value, ch.iid.value)
        ids -> (ch.characteristic, toEventable(ch.characteristic))
      }
    }
    flattenedTuples.toMap
  }

  private object Num {
    def unapply(text: String): Option[Int] = Try(text.toInt).toOption
  }

  def listAllAccessories(): Future[HapJsonResponse] = AccessoryJson.list(root.accessories) map respond

  def getCharacteristicsValues(ids: String): Future[HapJsonResponse] = {
    val found = ids.split(',').toSeq map (_.split('.').toSeq) collect {
        case Seq(Num(aid), Num(iid)) => aid -> iid
      } flatMap {
      case (aid, iid) => characteristicsAsMap.get(ChContext(aid, iid)) map (_._1.readJsonValue()) map ((aid, iid, _))
    }
    log.debug(s"Attempting to get values of characteristics $ids")
    AccessoryJson.characteristicsValues(found) map respond
  }

  private def respond(obj: JsObject): HapJsonResponse = new HapJsonResponse(obj.printJsonBytes)

  def putCharacteristics(body: Array[Byte], clientConnection: HomekitClientConnection): HttpResponse = {

    subscriptions.batchUpdate()

    try {
      val request = body.parseJsonTo[CharacteristicsPutRequest]
      log.debug(s"Putting characteristic values or EVs:\n$request")

      val withMatchedCharacteristics = request.characteristics flatMap { update =>
        characteristicsAsMap.get(ChContext(update.aid, update.iid)) map (update -> _)
      }

      withMatchedCharacteristics foreach { case (update, (characteristic, maybeEventable)) =>

        maybeEventable zip update.ev foreach {
          case (eventable, true) => subscriptions.addSubscription(update.aid, update.iid, eventable, clientConnection)
          case (eventable, false) => subscriptions.removeSubscription(eventable, clientConnection)
        }

        update.value foreach { v =>
          characteristic.jsonWriter foreach (_.apply(v)) //TODO wait for the future and potentially evaluate the error
        }
      }
    } finally subscriptions.completeUpdateBatch()

    new HapJsonNoContentResponse
  }
}

object AccessoryDatabase {

  case class SingleCharacteristicAdjustment(aid: Int, iid: Int, ev: Option[Boolean], value: Option[JsValue])
  case class CharacteristicsPutRequest(characteristics: Seq[SingleCharacteristicAdjustment])

  private case class ChContext(aid: Int, iid: Int)
}
