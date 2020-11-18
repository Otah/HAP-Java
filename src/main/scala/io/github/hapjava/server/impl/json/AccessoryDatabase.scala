package io.github.hapjava.server.impl.json

import com.github.otah.hap.api._
import com.github.otah.hap.api.json.AccessoryJson
import com.github.otah.hap.api.server.HomeKitRoot
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback
import io.github.hapjava.server.impl.connections.SubscriptionManager
import io.github.hapjava.server.impl.http.{HomekitClientConnection, HttpResponse}
import spray.json._

import scala.concurrent._
import scala.util.Try

class AccessoryDatabase(
  root: HomeKitRoot, subscriptions: SubscriptionManager
)(implicit
  ec: ExecutionContext, formatter: JsonFormatter
) {
  import AccessoryDatabase._

  private object JsonProtocol extends DefaultJsonProtocol {
    implicit val format1 = jsonFormat4(SingleCharacteristicAdjustment)
    implicit val format2 = jsonFormat1(CharacteristicsPutRequest)
  }
  import JsonProtocol._

  private def toEventable(characteristic: LowLevelCharacteristic) =
    new io.github.hapjava.characteristics.EventableCharacteristic {

      override def subscribe(callback: HomekitCharacteristicChangeCallback): Unit = xxx

      override def unsubscribe(): Unit = xxx
    }

  private val characteristicsAsMap = root.accessories.flatMap {
    case (InstanceId(aid), accessory) =>
      val flatCharacteristics = accessory.services flatMap {
        case (_, service) => service.characteristics
      }
      flatCharacteristics map {
        case (InstanceId(iid), characteristic) => (aid, iid) -> (characteristic, toEventable(characteristic))
      }
  }.toMap

  private object Num {
    def unapply(text: String): Option[Int] = Try(text.toInt).toOption
  }

  def listAllAccessories(): Future[HapJsonResponse] = AccessoryJson.list(root.accessories) map respond

  def getCharacteristicsValues(ids: String): Future[HapJsonResponse] = {
    val found = ids.split(',').toSeq map (_.split('.').toSeq) collect {
        case Seq(Num(aid), Num(iid)) => aid -> iid
      } flatMap {
      case tuple @ (aid, iid) => characteristicsAsMap.get(tuple) map (_._1.readJsonValue()) map ((aid, iid, _))
    }
    AccessoryJson.characteristicsValues(found) map respond
  }

  private def respond(obj: JsObject): HapJsonResponse = new HapJsonResponse(obj.printJsonBytes)

  def putCharacteristics(body: Array[Byte], clientConnection: HomekitClientConnection): HttpResponse = {

    subscriptions.batchUpdate()

    try {
      val request = body.parseJsonTo[CharacteristicsPutRequest]

      val withMatchedCharacteristics = request.characteristics flatMap { update =>
        characteristicsAsMap.get((update.aid, update.iid)) map (update -> _)
      }

      withMatchedCharacteristics foreach { case (update, (characteristic, eventable)) =>
        import update._

        ev foreach {
          case true => subscriptions.addSubscription(aid, iid, eventable, clientConnection)
          case false => subscriptions.removeSubscription(eventable, clientConnection)
        }

        value foreach { v =>
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
}
