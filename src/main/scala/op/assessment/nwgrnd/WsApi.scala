package op.assessment.nwgrnd

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream._
import op.assessment.nwgrnd.WsApi._

object WsApi {

  trait ClientIn
  trait ClientOut

  abstract class WsIn(val $type: String) extends ClientIn
  case class Login(name: String, pass: String) extends WsIn("login")
  case class Ping(seq: Int) extends WsIn("ping")
  case object Subscribe extends WsIn("subscribe_tables")
  case object Unsubscribe extends WsIn("unsubscribe_tables")
  case class Update(table: Table) extends WsIn("update_table") with ClientOut
  case class Remove(id: String) extends WsIn("remove_table") with ClientOut

  trait WsOut extends ClientOut
  case object LoginFailed extends WsOut
  case class LoginSuccessful(userType: String) extends WsOut
  case class Pong(seq: Int) extends WsOut
  case class Table(id: Int, name: String, participants: Int) extends WsOut
  case class Tables(tables: List[Table]) extends WsOut
  case class Updated(table: Table) extends WsOut with ClientIn
  case class Removed(id: String) extends WsOut with ClientIn
}

trait WsApi extends JsonSupport { this: Security =>

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  import akka.http.scaladsl.server.Directives._
  import system.dispatcher

  val tablesRepo: ActorRef

  val route: Route =
    path("ws-api") {
      handleWebSocketMessages(clientHandler)
    }

  def tables: Sink[(ClientContext, WsIn), NotUsed] = {
    Flow[(ClientContext, WsIn)].collect {
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
      .map((out: WsOut) => TextMessage(marshal(out)))

  def clientHandler: Flow[Message, TextMessage, NotUsed] =
    Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(2)(in => in.runFold("")(_ + _).map(unmarshal))
      .merge(subscription)
      .via(new ClientFlow)
      .collect({ case out: WsOut => out })
      .map(out => TextMessage(marshal(out)))

  class ClientFlow extends GraphStage[FlowShape[ClientIn, ClientOut]] {

    private[this] val in = Inlet[ClientIn]("ClientFlow.in")
    private[this] val out = Outlet[ClientOut]("ClientFlow.out")
    val shape: FlowShape[ClientIn, ClientOut] = FlowShape.of(in, out)

    def createLogic(
      inheritedAttributes: Attributes
    ): GraphStageLogic = new GraphStageLogic(shape) {

      private[this] val ctx = new ClientContext

      setHandler(in, new InHandler {
        def onPush(): Unit = {
          grab(in) match {
            case Login(name, pass) =>
              authService.auth(name, pass) match {
                case p @ Some(principal) =>
                  ctx.principal = p
                  push(out, LoginSuccessful(principal.role))
                case None => push(out, LoginFailed)
              }
            case Ping(seq) if ctx.principal.nonEmpty => push(out, Pong(seq))
            case _ => pull(in)
          }
        }
      })

      setHandler(out, new OutHandler {
        def onPull(): Unit = pull(in)
      })
    }
  }
}
