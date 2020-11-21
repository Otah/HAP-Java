package io.github.otah.hap.test

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import com.github.otah.hap.api._
import com.github.otah.hap.api.characteristics.PowerStateCharacteristic
import com.github.otah.hap.api.information.Revision
import com.github.otah.hap.api.server._
import com.github.otah.hap.api.services.experimental._

import scala.concurrent.Future

object TestRunner extends App {

  val switch1 = new HomeKitAccessory with SingleServiceAccessory with SwitchService with SequenceInstanceIds {

    override val info: HomeKitInfo = new HomeKitInfo {
      override def identification: () => Unit = () => println("Hello switch!")
      override def label: String = "Switch1"
      override def serialNumber: String = "none"
      override def model: String = "testswitch1"
      override def manufacturer: String = "Otah"
      override def firmwareRevision: Revision = Revision("0.1")
      override def hardwareRevision: Option[Revision] = None
    }

    override val powerState: PowerStateCharacteristic = new PowerStateCharacteristic {

      private val notifierCounter = new AtomicInteger(1)
      private val state = new AtomicBoolean(false)
      private val notifiers = new ConcurrentHashMap[Int, Boolean => Future[Unit]]()

      override def reader: Some[Reader] = Reader(Future.successful(state.get()))

      override def writer: Some[Writer] = Writer { newValue =>
        state.set(newValue)
        println(s"Set new value: $newValue")
        notifiers.values().forEach(n => Future.successful(n(newValue)))
        Future.unit
      }

      override def notifier: Some[Notifier] = Some { (callback: Boolean => Future[Unit]) =>
        new Subscription {
          private val id = notifierCounter.getAndIncrement()
          notifiers.put(id, callback)

          override def unsubscribe(): Unit = notifiers.remove(id)
        }
      }
    }
  }

  val bridge = new HomeKitBridge {
    override def info: HomeKitInfo = new HomeKitInfo {
      override def identification: () => Unit = () => println("Hello bridge!")
      override def label: String = "Bridge1"
      override def serialNumber: String = "none"
      override def model: String = "testbridge1"
      override def manufacturer: String = "Otah"
      override def firmwareRevision: Revision = Revision("0.1")
      override def hardwareRevision: Option[Revision] = None
    }
    override def accessories: Seq[Identified[HomeKitAccessory]] = Seq(
      3 <=> switch1,
    )
  }

  import io.github.hapjava.server.impl.{HomekitUtils => utils}
  val pin = utils.generatePin()

  println(s"PIN: $pin")

  val auth = HomeKitAuthentication(
    AuthInfoStorage(initialUserKeys = Map.empty, onChange = _ => ()),
    AuthSecurityInfo(utils.generateMac(), utils.generateSalt(), utils.generateKey().toSeq, pin),
  )

  val serverDefinition = HomeKitServer(port = 1234, host = Some("10.11.0.156"))(HomeKitRoot.bridge(bridge, auth))

  val server = new io.github.hapjava.server.impl.HomekitServer(serverDefinition)
  server.start()
}
