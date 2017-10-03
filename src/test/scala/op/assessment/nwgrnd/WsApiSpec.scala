package op.assessment.nwgrnd

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ ScalatestRouteTest, WSProbe }
import org.scalatest.{ Matchers, WordSpec }

class WsApiSpec extends WordSpec with Matchers
    with Directives with ScalatestRouteTest with WsApi {

  "WsApi ping-pong" in {
    val wsClient = WSProbe()

    WS("/ws-api", wsClient.flow) ~> route ~>
      check {
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
}
