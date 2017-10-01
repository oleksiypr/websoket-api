package op.assessment.nwgrnd

import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ ScalatestRouteTest, WSProbe }
import akka.util.ByteString
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import spray.json._

class WsApiSpec extends WordSpec with Matchers
    with Directives with ScalatestRouteTest with WsApi {

  "WsApi" in {
    val wsClient = WSProbe()

    WS("/ws-api", wsClient.flow) ~> route ~>
      check {
        isWebSocketUpgrade shouldEqual true

        /*        wsClient.sendMessage(
          """{
            | "$type":"login",
            | "username":"user1234",
            | "password":"password12345"
            |}""".stripMargin
        )

        wsClient.expectMessage(
          """{"$type":"login_failed"}"""
        )*/

        wsClient.sendMessage(
          """{
            | "$type":"login",
            | "username":"user1234",
            | "password":"password1234"
            }""".stripMargin
        )

        wsClient.expectMessage(
          """{"$type":"login_successful","user_type":"admin"}"""
        )

        wsClient.sendCompletion()
        wsClient.expectCompletion()
      }
  }
}
