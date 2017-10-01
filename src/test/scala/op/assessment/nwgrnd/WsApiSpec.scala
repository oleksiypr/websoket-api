package op.assessment.nwgrnd

import scala.concurrent.duration._
import akka.util.ByteString
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ ScalatestRouteTest, WSProbe }
import org.scalatest.{ Matchers, WordSpec }

class WsApiSpec extends WordSpec with Matchers
    with Directives with ScalatestRouteTest with WsApi {

  "WsApi" in {
    val wsClient = WSProbe()

    // WS creates a WebSocket request for testing
    WS("/greeter", wsClient.flow) ~> route ~>
      check {
        // check response for WS Upgrade headers
        isWebSocketUpgrade shouldEqual true

        // manually run a WS conversation
        wsClient.sendMessage("Peter")
        wsClient.expectMessage("Hello Peter!")

        wsClient.sendMessage(BinaryMessage(ByteString("abcdef")))
        wsClient.expectNoMessage(100.millis)

        wsClient.sendMessage("John")
        wsClient.expectMessage("Hello John!")

        wsClient.sendCompletion()
        wsClient.expectCompletion()
      }
  }
}
