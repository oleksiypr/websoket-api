package op.assessment.nwgrnd

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import op.assessment.nwgrnd.WsApi._

object WsApi {

  abstract class Incoming(val $type: String)
  case class Login(name: String, pass: String) extends Incoming("login")
  case class Ping(seq: Int) extends Incoming("ping")
  case object Subscribe extends Incoming("subscribe_tables")
  case object Unsubscribe extends Incoming("unsubscribe_tables")
  case class Update(table: Table) extends Incoming("update_table")
  case class Remove(id: String) extends Incoming("remove_table")

  trait Outcoming
  case object LoginFailed extends Outcoming
  case class LoginSuccessful(userType: String) extends Outcoming
  case class Pong(seq: Int) extends Outcoming
  case class Table(id: Int, name: String, participants: Int) extends Outcoming
  case class Tables(tables: List[Table]) extends Outcoming
  case class Updated(table: Table) extends Outcoming
  case class Removed(id: String) extends Outcoming
}

trait WsApi extends JsonSupport { this: Security =>

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  import akka.http.scaladsl.server.Directives._
  import system.dispatcher

  val tablesRepo: ActorRef

  val route: Route =
    path("ws-api") {
      handleWebSocketMessages(handler)
    }

  def tables: Sink[(ClientContext, Incoming), NotUsed] = {
    Flow[(ClientContext, Incoming)].collect {
      case (ctx @ ClientContext(_), Subscribe) =>
        ctx.isSubscribed = true
        Subscribe
      case (ctx @ ClientContext(_), Unsubscribe) =>
        ctx.isSubscribed = false
    }.to(Sink.actorRef(tablesRepo, 'sinkclose))
  }

  def subscription: Source[Nothing, ActorRef] =
    Source.actorRef(8, OverflowStrategy.fail)
      .mapMaterializedValue { sourceActor ⇒
        tablesRepo ! ('income → sourceActor)
        sourceActor
      }

  def handler: Flow[Message, Message, Any] =
    Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(2)(in => in.runFold("")(_ + _).map(unmarshal))
      .statefulMapConcat(() ⇒ {
        val context = new ClientContext
        m ⇒ (context → m) :: Nil
      })
      .mapConcat {
        case m @ (c: ClientContext, Login(name, pass)) =>
          c.principal = authService.auth(name, pass)
          m :: Nil
        case m @ (_: ClientContext, _) => m :: Nil

      }
      .alsoTo(tables)
      .collect {
        case (ClientContext(auth), Login(_, _)) ⇒ LoginSuccessful(auth.role)
        case (_: ClientContext, Login(_, _)) => LoginFailed
        case (c: ClientContext, Ping(seq)) if c.principal.nonEmpty => Pong(seq)
      }
      .merge(subscription)
      .map(out => TextMessage(marshal(out)))
}
