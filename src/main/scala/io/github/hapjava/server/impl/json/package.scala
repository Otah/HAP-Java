package io.github.hapjava.server.impl

import java.nio.charset.StandardCharsets

import spray.json._

package object json {

  private val utf8 = StandardCharsets.UTF_8

  implicit class JsValueExt(val jsValue: JsValue) {

    def printJsonBytes(implicit format: JsonFormatter): Array[Byte] = format(jsValue) getBytes utf8
  }

  implicit class ByteArrayExt(val arr: Array[Byte]) {

    def parseJson: JsValue = new String(arr, utf8).parseJson

    def parseJsonTo[T](implicit reader: JsonReader[T]): T = parseJson.convertTo[T]
  }
}
