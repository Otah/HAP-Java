package io.github.hapjava.server.impl.json

import java.io.ByteArrayOutputStream
import javax.json.Json

import com.github.otah.hap.api.InstanceId
import com.github.otah.hap.api.json.AccessoryJson
import com.github.otah.hap.api.server.HomeKitRoot
import sjsonnew.shaded.scalajson.ast.JObject

import scala.concurrent._
import scala.util.Try

class AccessoryDatabase(root: HomeKitRoot)(implicit ec: ExecutionContext) {

  private val characteristicsAsMap = root.accessories.flatMap {
    case (InstanceId(aid), accessory) =>
      accessory.services flatMap {
        case (_, service) => service.characteristics
      } map {
        case (InstanceId(iid), characteristic) => (aid, iid) -> characteristic
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
      case tuple @ (aid, iid) => characteristicsAsMap.get(tuple) map (_.readJsonValue()) map ((aid, iid, _))
    }
    AccessoryJson.characteristicsValues(found) map respond
  }

  private def respond(obj: JObject): HapJsonResponse = new HapJsonResponse(jsonToBytes(obj))

  private def jsonToBytes(obj: JObject): Array[Byte] = {
    val oStream = new ByteArrayOutputStream
    try {
      Json.createWriter(oStream).write(JsonConverters.convertObjectJToJson(obj))
      oStream.toByteArray
    } finally Try(oStream.close())
  }
}
