package op.assessment.nwgrnd

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.stream._
import akka.stream.scaladsl.{ Flow, Sink, Source }
import op.assessment.nwgrnd.WsApi._
import scala.concurrent.Future

object WsApi {

  sealed trait ClientIn
  sealed trait ClientOut

  sealed abstract class WsIn(val $type: String) extends ClientIn
  case class Login(name: String, pass: String) extends WsIn("login")
  case class Ping(seq: Int) extends WsIn("ping")

  sealed trait TableCommand
  case object Subscribe extends WsIn("subscribe_tables") with TableCommand with ClientOut
  case object Unsubscribe extends WsIn("unsubscribe_tables") with TableCommand
  case class Update(table: Table) extends WsIn("update_table") with TableCommand with ClientOut
  case class Remove(id: String) extends WsIn("remove_table") with TableCommand with ClientOut

  sealed trait WsOut extends ClientOut
  case object LoginFailed extends WsOut
  case class LoginSuccessful(userType: String) extends WsOut
  case class Pong(seq: Int) extends WsOut

  sealed trait TableEvent extends WsOut with ClientIn
  case class Table(id: Int, name: String, participants: Int) extends WsOut
  case class Subscribed(tables: List[Table]) extends TableEvent
  case class Updated(table: Table) extends TableEvent
  case class Removed(id: String) extends TableEvent
}

trait WsApi extends JsonSupport { this: Service =>

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  import akka.http.scaladsl.server.Directives._
  import system.dispatcher

  val tablesRepo: ActorRef

  val route: Route =
    path("ws-api") {
      handleWebSocketMessages(clientHandler)
    }

  def tables: Sink[ClientOut, NotUsed] = {
    Flow[ClientOut].collect {
      case in: TableCommand => in
    }.to(Sink.actorRef(tablesRepo, 'sinkclose))
  }

  def subscription: Source[Nothing, ActorRef] =
    Source.actorRef(8, OverflowStrategy.fail)
      .mapMaterializedValue { sourceActor ⇒
        tablesRepo ! ('income → sourceActor)
        sourceActor
      }

  def clientFlow: ClientFlow = new ClientFlow(security)

  def clientHandler: Flow[Message, TextMessage, NotUsed] =
    Flow[Message]
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
