package io.github.hapjava.server.impl.json

import io.github.hapjava.server.impl.connections.PendingNotification
import io.github.hapjava.server.impl.http.HttpResponse
import spray.json._

class EventController(implicit format: JsonFormatter) {

  // using array for easier interoperability
  def createMessage(notifications: Array[PendingNotification]): HttpResponse = {

    val jsonNotifications = notifications.toVector map { n =>
      JsObject(
        "aid" -> JsNumber(n.aid),
        "iid" -> JsNumber(n.iid),
        "value" -> n.changed,
      )
    }

    val jsonRoot = JsObject("characteristics" -> JsArray(jsonNotifications))

    new EventResponse(jsonRoot.printJsonBytes)
  }
}
