package op.assessment.nwgrnd

import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import op.assessment.nwgrnd.WsApi._

final class ClientFlow private[nwgrnd] (security: Security)
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
          case Login(name, pass) =>
            security.auth(name, pass) match {
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