package op.assessment.nwgrnd.repo

import akka.actor.{ Actor, ActorRef, Terminated }
import op.assessment.nwgrnd.ws.WsApi._

class TablesRepo extends Actor {

  private[this] var tablesState = TablesState()
  private[this] var subscribers = Set.empty[ActorRef]

  override def postStop(): Unit = {
    subscribers.foreach(context.stop)
  }

  val receive: Receive = {
    case ('income, a: ActorRef) ⇒
      subscribers += context.watch(a)

    case cmd: TableCommand =>
      val (res, newState) = tablesState(cmd)
      publish(res)
      tablesState = newState

    case 'sinkclose ⇒
      context.stop(self)

    case Terminated(a) =>
      subscribers -= a
  }

  private def publish(res: TableResult) {
    subscribers.foreach(_ ! res)
  }
}
