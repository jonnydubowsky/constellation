package org.constellation.p2p

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef}
import akka.io.{IO, Udp}
import akka.serialization.SerializationExtension
import akka.util.{ByteString, Timeout}
import org.constellation.p2p.PeerToPeer.Id

case class UDPMessage(data: Any, remote: InetSocketAddress)
case class GetUDPSocketRef()
case class UDPSend(data: ByteString, remote: InetSocketAddress)
case class UDPSendToIDByte(data: ByteString, remote: Id)
case class UDPSendToID[T](data: T, remote: Id)
case class RegisterNextActor(nextActor: ActorRef)
case class GetSelfAddress()
case class Ban(address: InetSocketAddress)
case class GetBanList()

// This is using java serialization which is NOT secure. We need to update to use another serializer
// Examples below:
// https://github.com/calvinlfer/Akka-Persistence-example-with-Protocol-Buffers-serialization/blob/master/src/main/scala/com/experiments/calculator/serialization/CalculatorEventProtoBufSerializer.scala
// https://github.com/dnvriend/akka-serialization-test/tree/master/src/main/scala/com/github/dnvriend/serializer
// Serialization below is just a temporary hack to avoid having to make more changes for now.

class UDPActor(
                @volatile var nextActor: Option[ActorRef] = None,
                port: Int = 16180,
                bindInterface: String = "0.0.0.0"
              ) extends Actor {

  import context.system

  private val address = new InetSocketAddress(bindInterface, port)
  IO(Udp) ! Udp.Bind(self, address, List(
  //  Udp.SO.ReceiveBufferSize(1024 * 1024 * 20),
  //  Udp.SO.SendBufferSize(1024 * 1024 * 20),
    Udp.SO.ReuseAddress.apply(true))
  )

  @volatile var udpSocket: ActorRef = _
  @volatile var bannedIPs: Seq[InetSocketAddress] = Seq.empty[InetSocketAddress]
  implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)

  import constellation._

  def receive: PartialFunction[Any, Unit] = {
    case Udp.Bound(_) =>
      val ref = sender()
      udpSocket = ref
      context.become(ready(ref))
    case RegisterNextActor(next) =>
     // println(s"Registered next actor for udp on port $port")
      nextActor = Some(next)

  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
     // println(s"Received UDP message from $remote -- sending to $nextActor")
      if (!bannedIPs.contains(remote)) {
        val str = data.utf8String
        val serialization = SerializationExtension(context.system)
        val serMsg = str.x[SerializedUDPMessage]
        val deser = serialization.deserialize(serMsg.data, serMsg.serializer, Some(classOf[Any]))
        deser.foreach { d =>
          nextActor.foreach { n => n ! UDPMessage(d, remote) }
        }
      } else {
        println(s"BANNED MESSAGE DETECTED FROM $remote")
      }
    case Udp.Unbind => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
    case GetUDPSocketRef => sender() ! udpSocket
    case UDPSend(data, remote) => {
   //   println(s"Attempting UDPSend from port: $port to $remote of data length: ${data.length}")
      import akka.pattern.ask
      //val res = (socket ? Udp.Send(data, remote)).get()
      val res = (socket ! Udp.Send(data, remote))
    //  println(s"UDPSend Response $res")

    }
    case RegisterNextActor(next) => {
    //  println(s"Registered next actor for udp on port $port")
      nextActor = Some(next)
    }
    case GetSelfAddress => sender() ! address
    case Ban(remote) => bannedIPs = {bannedIPs ++ Seq(remote)}.distinct
    case GetBanList => sender() ! bannedIPs
    case u: UDPSendToIDByte => {
     // println("UDPSendToID: " + u)
      nextActor.foreach{ na => na ! u}
    }
    case x =>
      println(s"UDPActor unrecognized message: $x")

  }
}

case class SerializedUDPMessage(data: Array[Byte], serializer: Int)