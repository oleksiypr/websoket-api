package op.assessment.nwgrnd

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ ScalatestRouteTest, WSProbe }
import akka.stream.ActorMaterializer
import akka.testkit.{ TestActor, TestProbe }
import op.assessment.nwgrnd.WsApi.Tables
import org.scalatest.{ Matchers, WordSpec }

class WsApiSpec extends WordSpec with Matchers
    with Directives with ScalatestRouteTest { self =>

  "WsApi ping-pong" in new WsApi {
    implicit val system: ActorSystem = self.system
    implicit val materializer: ActorMaterializer = self.materializer

    val probe = TestProbe()
    val tables: ActorRef = probe.ref
    val wsClient = WSProbe()

    WS("/ws-api", wsClient.flow) ~> route ~> check {
      isWebSocketUpgrade shouldEqual true

      val source = probe.expectMsgPF() {
        case ('income, source: ActorRef) => source
      }

      wsClient.sendMessage(
        """{
          | "$type":"login",
          | "username":"user1234",
          | "password":"password12345"
          |}""".stripMargin
      )

      wsClient.expectMessage(
        """{"$type":"login_failed"}"""
      )

      wsClient.sendMessage(
        """{
          | "$type":"login",
          | "username":"user",
          | "password":"password-user"
          }""".stripMargin
      )

      wsClient.expectMessage(
        """{"$type":"login_successful","user_type":"user"}"""
      )

      wsClient.sendMessage(
        """{
          | "$type": "ping",
          | "seq": 1
          |}
        """.stripMargin
      )

      wsClient.expectMessage("""{"$type":"pong","seq":1}""")

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }

  "WsApi subscribe" in new WsApi {
    implicit val system: ActorSystem = self.system
    implicit val materializer: ActorMaterializer = self.materializer

    val probe = TestProbe()
    val tables: ActorRef = probe.ref
    val wsClient = WSProbe()

    WS("/ws-api", wsClient.flow) ~> route ~> check {
      isWebSocketUpgrade shouldEqual true

      val source: ActorRef = probe.expectMsgPF() {
        case ('income, source: ActorRef) => source
      }
      probe.setAutoPilot(
        (_: ActorRef, msg: Any) => msg match {
          case "update" =>
            probe.send(source, Tables)
            TestActor.KeepRunning
          case 'sinkclose => TestActor.NoAutoPilot
          case x => println("!!!" + x); TestActor.KeepRunning
        }
      )

      wsClient.sendMessage(
        """{
          | "$type":"login",
          | "username":"user",
          | "password":"password-user"
          }""".stripMargin
      )

      wsClient.expectMessage(
        """{"$type":"login_successful","user_type":"user"}"""
      )

      wsClient.sendMessage(
        """{
          | "$type": "subscribe_tables"
          |}
        """.stripMargin
      )

      wsClient.expectMessage("""{"$type":"table_list"}""")

      //TODO: handle this scenario
      //probe.send(source, TextMessage.Strict("subscribed"))
      //wsClient.expectMessage("subscribed")

      tables ! "update"
      wsClient.expectMessage("""{"$type":"table_list"}""")

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }
}
