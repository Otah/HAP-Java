package io.github.otah.hap.test

import com.github.otah.hap.api._
import com.github.otah.hap.api.characteristics.PowerStateCharacteristic
import com.github.otah.hap.api.server._
import com.github.otah.hap.api.services.SwitchService

object TestRunner extends App {

  val switch1 = new HomeKitAccessory with SingleServiceAccessory with SwitchService {

    override def info: HomeKitInfo = ???
    override def services: Services = ???

    override def powerState: Required[PowerStateCharacteristic] = 20 <=> new PowerStateCharacteristic {

      override def reader: Some[Reader] = ???

      override def writer: Some[Writer] = ???

      override def notifier: Some[Notifier] = ???
    }
  }

  val bridge = ???
  val auth = ???

  val serverDefinition = HomeKitServer(1234)(HomeKitRoot.bridge(bridge, auth))

  val server = new io.github.hapjava.server.impl.HomekitServer(serverDefinition)
  server.start()
}
