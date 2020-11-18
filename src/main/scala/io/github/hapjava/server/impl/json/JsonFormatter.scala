package io.github.hapjava.server.impl.json

import spray.json.JsValue

trait JsonFormatter extends (JsValue => String)
