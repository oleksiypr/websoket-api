package op.assessment.nwgrnd

import akka.actor.ActorRef
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ ScalatestRouteTest, WSProbe }
import akka.testkit.TestProbe
import org.scalatest.{ Matchers, WordSpec }

class WsApiSpec extends WordSpec with Matchers
    with Directives with ScalatestRouteTest with WsApi {

  val probe = TestProbe()
  val tables: ActorRef = probe.ref

  "WsApi ping-pong" in {
    val wsClient = WSProbe()

    WS("/ws-api", wsClient.flow) ~> route ~> check {
      isWebSocketUpgrade shouldEqual true

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
      wsClient.expectCompletion()
    }
  }

  "WsApi subscribe" in {
    val wsClient = WSProbe()

    WS("/ws-api/subscribe", wsClient.flow) ~> route ~> check {
      isWebSocketUpgrade shouldEqual true

      val source = probe.expectMsgPF() {
        case ('income, source: ActorRef) => source
      }
      probe.send(source, TextMessage.Strict("subscribed"))

      wsClient.expectMessage("subscribed")

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }
}
