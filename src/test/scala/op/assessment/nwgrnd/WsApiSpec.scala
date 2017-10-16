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

  "WsApi ping-pong" in new WsApi with SimpleService {
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

      loginFailed(wsClient)

      wsClient.sendMessage("""{"$type": "ping", "seq": 1 }""")
      wsClient.expectNoMessage()

      userLoginSucceed(wsClient)

      wsClient.sendMessage("""{ "$type": "ping", "seq": 1 }""")
      wsClient.expectJsonStr("""{"$type": "pong", "seq": 1}""")

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }

  "WsApi subscribe" in new WsApi with SimpleService {
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
            probe.send(source, Subscribed(
              List(IdTable(id = 1, "table -James Bond", 7))
            )); TestActor.KeepRunning
          case Update(table) =>
            probe.send(source, Updated(table)); TestActor.KeepRunning
          case 'sinkclose => TestActor.NoAutoPilot
          case _ => TestActor.KeepRunning
        }
      )

      userLoginSucceed(wsClient)

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

      tablesRepo ! Update(IdTable(id = 1, "table -James Bond", 7))

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
      wsClient.expectNoMessage()

      tablesRepo ! Update(IdTable(id = 1, "table -James Bond 2", 8))

      wsClient.sendMessage("""{ "$type": "ping", "seq": 1 }""")
      wsClient.expectJsonStr("""{"$type": "pong", "seq": 1}""")

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }

  "WsApi commands" in new WsApi with SimpleService {
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
          case Add(afterId, table) =>
            probe.send(
              source,
              Added(afterId, IdTable(id = 3, table.name, table.participants))
            )
            TestActor.KeepRunning
          case 'sinkclose => TestActor.NoAutoPilot
          case _ => TestActor.KeepRunning
        }
      )

      userLoginSucceed(wsClient)

      wsClient.sendMessage(
        """{
          | "$type": "add_table",
          | "after_id": 1,
          | "table": {
          |   "name": "table -James Bond",
          |   "participants": 7
          | }
          |}""".stripMargin
      )
      wsClient.expectJsonStr("""{"$type": "not_authorized"}""")

      adminLoginSucceed(wsClient)

      wsClient.sendMessage(
        """{
          | "$type": "add_table",
          | "after_id": 1,
          | "table": {
          |   "name": "table -James Bond",
          |   "participants": 7
          | }
          |}""".stripMargin
      )
      probe.expectMsg(
        Add(
          afterId = 1,
          Table(name = "table -James Bond", participants = 7)
        )
      )

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }

  "WsApi commands failed" in new WsApi with SimpleService {
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
          case Update(table) =>
            probe.send(source, UpdateFailed(table.id)); TestActor.KeepRunning
          case Remove(id) =>
            probe.send(source, RemovalFailed(id)); TestActor.KeepRunning
          case 'sinkclose => TestActor.NoAutoPilot
          case _ => TestActor.KeepRunning
        }
      )
      adminLoginSucceed(wsClient)

      wsClient.sendMessage(
        """{
         |  "$type": "update_table",
         |  "table": {
         |    "id": 3,
         |    "name": "table -Foo Fighters",
         |    "participants": 4
         |  }
         |}""".stripMargin
      )
      wsClient.expectJsonStr("""{"$type": "update_failed","id": 3}""")

      wsClient.sendMessage(
        """{
         |  "$type": "remove_table",
         |  "id": 3
         |}""".stripMargin
      )
      wsClient.expectJsonStr("""{"$type": "removal_failed","id": 3}""")

      wsClient.sendCompletion()
      system.stop(source)
      wsClient.expectCompletion()
    }
  }

  private def loginFailed(wsClient: WSProbe): Unit = {
    wsClient.sendMessage(
      """{
        | "$type":"login",
        | "username":"user1234",
        | "password":"password12345"
        |}""".
      stripMargin
    )
    wsClient.expectJsonStr(
      """{"$type":"login_failed"}"""
    )
  }

  private def userLoginSucceed(wsClient: WSProbe): Unit = {
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
  }

  private def adminLoginSucceed(wsClient: WSProbe): Unit = {
    wsClient.sendMessage(
      """{
          | "$type":"login",
          | "username":"admin",
          | "password":"password-admin"
          }""".stripMargin
    )
    wsClient.expectJsonStr(
      """{"$type": "login_successful", "user_type": "admin"}"""
    )
  }
}
