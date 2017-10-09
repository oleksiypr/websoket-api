package op.assessment.nwgrnd

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ ScalatestRouteTest, WSProbe }
import akka.stream.ActorMaterializer
import akka.testkit.{ TestActor, TestProbe }
import op.assessment.nwgrnd.WsApi._
import org.scalatest.{ Matchers, WordSpec }
import spray.json._

object WsApiSpec {

  implicit class WsClientOps(val probe: WSProbe) extends AnyVal {
    def expectJsonStr(json: String)(implicit matchers: Matchers) {
      import matchers._
      probe.expectMessage() match {
        case TextMessage.Strict(msg) =>
          msg.parseJson.asJsObject should be {
            json.stripMargin.parseJson.asJsObject
          }
        case _ => fail
      }
    }
  }
}

class WsApiSpec extends WordSpec with Matchers
    with Directives with ScalatestRouteTest { self =>

  import WsApiSpec._
  implicit val matchers: Matchers = this

  "WsApi ping-pong" in new WsApi with SimpleSecurity {
    implicit val system: ActorSystem = self.system
    implicit val materializer: ActorMaterializer = self.materializer

    val probe = TestProbe()
    val tablesRepo: ActorRef = probe.ref
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
      wsClient.expectJsonStr("""{"$type":"login_failed"}""")

      wsClient.sendMessage("""{"$type": "ping", "seq": 1 }""")
      wsClient.expectNoMessage()

      wsClient.sendMessage(
        """{
          | "$type":"login",
          | "username":"user",
          | "password":"password-user"
          }""".stripMargin
      )
      wsClient.expectJsonStr(
        """{"$type": "login_successful", "user_type": "user"}"""
      )

      wsClient.sendMessage("""{ "$type": "ping", "seq": 1 }""")
      wsClient.expectJsonStr("""{"$type": "pong", "seq": 1}""")

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }

  "WsApi subscribe" in new WsApi with SimpleSecurity {
    implicit val system: ActorSystem = self.system
    implicit val materializer: ActorMaterializer = self.materializer

    val probe = TestProbe()
    val tablesRepo: ActorRef = probe.ref
    val wsClient = WSProbe()

    WS("/ws-api", wsClient.flow) ~> route ~> check {
      isWebSocketUpgrade shouldEqual true

      val source: ActorRef = probe.expectMsgPF() {
        case ('income, source: ActorRef) => source
      }
      probe.setAutoPilot(
        (_: ActorRef, msg: Any) => msg match {
          case Subscribe =>
            probe.send(source, Tables(
              List(Table(id = 1, "table -James Bond", 7))
            )); TestActor.KeepRunning
          case Update(table) =>
            probe.send(source, Updated(table)); TestActor.KeepRunning

          case 'sinkclose => TestActor.NoAutoPilot
          case x => println("!!!! ->" + x); TestActor.KeepRunning
        }
      )

      wsClient.sendMessage(
        """{
          | "$type":"login",
          | "username":"user",
          | "password":"password-user"
          }""".stripMargin
      )

      wsClient.expectJsonStr(
        """{"$type":"login_successful", "user_type":"user"}"""
      )

      wsClient.sendMessage("""{"$type": "subscribe_tables"}""")

      wsClient.expectJsonStr(
        """{
          |"$type": "table_list",
          | "tables": [
          |   {
          |     "id": 1,
          |     "name": "table -James Bond",
          |     "participants": 7
          |   }
          | ]
          |}"""
      )

      tablesRepo ! Update(Table(id = 1, "table -James Bond", 7))

      wsClient.expectJsonStr(
        """{
          |"$type": "table_updated",
          | "table": {
          |   "id": 1,
          |   "name": "table -James Bond",
          |   "participants": 7
          | }
          |}""".stripMargin
      )

      wsClient.sendMessage("""{"$type": "unsubscribe_tables"}""")
      //tablesRepo ! Update(Table(id = 1, "table -James Bond", 7))
      wsClient.expectNoMessage()

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }
}
