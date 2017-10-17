package op.assessment.nwgrnd.repo

import akka.actor.{ Actor, ActorRef, Terminated }
import op.assessment.nwgrnd.ws.WsApi.{ Subscribe, Subscribed, Table }

class TablesRepo extends Actor {

  private[this] var tables = Vector.empty[Table]
  private[this] var source = Option.empty[ActorRef]

  override def preStart(): Unit = {
    context.become(awaiting)
  }

  override def postStop(): Unit = {
    source.foreach(context.stop)
  }

  def awaiting: Receive = {
    case ('income, a: ActorRef) ⇒
      source = Some(a)
      context.watch(a)
      context become receive
    case _ => sender ! 'not_ready
  }

  val receive: Receive = {
    case Subscribe => source.foreach(_ ! Subscribed(Nil))

    case 'sinkclose ⇒ context.stop(self)
    case Terminated(a) if source.contains(a) =>
      source = None
      context.stop(self)
  }
}
