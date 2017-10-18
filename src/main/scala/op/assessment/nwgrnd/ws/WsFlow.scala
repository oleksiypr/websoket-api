package op.assessment.nwgrnd.ws

import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import op.assessment.nwgrnd.ws.WsApi._

final class WsFlow private[ws] (security: Security)
    extends GraphStage[FlowShape[ClientIn, ClientOut]] {

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
          case Login(name, pass) => login(name, pass)
          case _ if ctx.principal.isEmpty => pull(in)
          case Ping(seq) => push(out, Pong(seq))

          case Subscribe =>
            ctx.isSubscribed = true
            push(out, Subscribe)

          case Unsubscribe =>
            ctx.isSubscribed = false
            pull(in)
          case tr: TableRelated => handle(tr)

          case _ => pull(in)
        }
      }
    })

    setHandler(out, new OutHandler {
      def onPull(): Unit = pull(in)
    })

    private def login(name: String, pass: String): Unit = {
      security.auth(name, pass) match {
        case p @ Some(principal) =>
          ctx.principal = p
          push(out, LoginSuccessful(principal.role))
        case None => push(out, LoginFailed)
      }
    }

    private def handle(tr: TableRelated): Unit = tr match {
      case tc: TableCommand with ClientOut =>
        ctx.becomeExpecting(tc)
        if (ctx.isAuthorized) push(out, tc)
        else push(out, NotAuthorized)
      case te: TableEvent =>
        ctx.unbecomeExpecting(te)
        if (ctx.isSubscribed) push(out, te)
        else pull(in)
      case rf: RemovalFailed => handleFailed(rf)
      case uf: UpdateFailed => handleFailed(uf)
    }

    private def handleFailed(
      failed: TableResult with ClientOut
    ): Unit = if (ctx.isExpecting(failed)) {
      ctx.unbecomeExpecting(failed)
      push(out, failed)
    }
  }
}
