package op.assessment.nwgrnd

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream._
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

  def clientHandler: Flow[Message, TextMessage, NotUsed] =
    Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(2)(in => in.runFold("")(_ + _).map(unmarshal))
      .merge(subscription)
      .via(new ClientFlow)
      .alsoTo(tables)
      .collect({ case out: WsOut => out })
      .mapAsync(2)(out => Future(TextMessage(marshal(out))))

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
            case _ if ctx.principal.isEmpty => pull(in)
            case Ping(seq) => push(out, Pong(seq))
            case Subscribe =>
              ctx.isSubscribed = true
              push(out, Subscribe)
            case Unsubscribe =>
              ctx.isSubscribed = false
              pull(in)
            case te: TableEvent if ctx.isSubscribed => push(out, te)
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
