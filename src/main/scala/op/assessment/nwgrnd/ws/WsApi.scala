package op.assessment.nwgrnd.ws

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.stream._
import akka.stream.scaladsl.{ Flow, Sink, Source }
import op.assessment.nwgrnd.ws.WsApi._

import scala.concurrent.Future

object WsApi {

  sealed trait ClientIn
  sealed trait ClientOut

  sealed abstract class WsIn(val $type: String) extends ClientIn
  case class Login(name: String, pass: String) extends WsIn("login")
  case class Ping(seq: Int) extends WsIn("ping")

  case class Table(name: String, participants: Int)
  case class IdTable(id: Int, name: String, participants: Int)

  trait TableRelated

  sealed trait TableCommand extends TableRelated
  case object Subscribe extends WsIn("subscribe_tables") with TableCommand with ClientOut
  case object Unsubscribe extends WsIn("unsubscribe_tables") with TableCommand
  case class Update(table: IdTable) extends WsIn("update_table") with TableCommand with ClientOut
  case class Remove(id: Int) extends WsIn("remove_table") with TableCommand with ClientOut
  case class Add(afterId: Int, table: Table) extends WsIn("add_table") with TableCommand with ClientOut

  trait TableResult extends TableRelated with ClientIn

  sealed trait WsOut extends ClientOut
  case object LoginFailed extends WsOut
  case object NotAuthorized extends WsOut
  case class LoginSuccessful(userType: String) extends WsOut
  case class Pong(seq: Int) extends WsOut
  case class UpdateFailed(id: Int) extends WsOut with TableResult
  case class RemovalFailed(id: Int) extends WsOut with TableResult

  sealed trait TableEvent extends WsOut with TableResult
  case class Subscribed(tables: List[IdTable]) extends TableEvent
  case class Added(afterId: Int, table: IdTable) extends TableEvent
  case class Updated(table: IdTable) extends TableEvent
  case class Removed(id: Int) extends TableEvent
}

trait WsApi extends JsonSupport { this: Service =>

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  import akka.http.scaladsl.server.Directives._
  import system.dispatcher

  val tablesRepo: ActorRef

  val route: Route =
    path("ws-api") {
      handleWebSocketMessages {
        clientHandler
      }
    }

  private def tables: Sink[ClientOut, NotUsed] = {
    Flow[ClientOut].collect {
      case in: TableCommand => in
    } to {
      Sink.actorRef(tablesRepo, 'sinkclose)
    }
  }

  private def subscription: Source[Nothing, ActorRef] =
    Source.actorRef(8, OverflowStrategy.fail)
      .mapMaterializedValue { sourceActor ⇒
        tablesRepo ! ('income → sourceActor)
        sourceActor
      }

  private def clientFlow = new WsFlow(security)

  private def clientHandler = Flow[Message]
    .collect {
      case tm: TextMessage ⇒ tm.textStream
    }
    .mapAsync(2) { in =>
      in.runFold("")(_ + _).map(unmarshal)
    }
    .merge(subscription)
    .via(clientFlow)
    .alsoTo(tables)
    .collect {
      case out: WsOut => out
    }
    .mapAsync(2) { out =>
      Future(TextMessage(marshal(out)))
    }
}
