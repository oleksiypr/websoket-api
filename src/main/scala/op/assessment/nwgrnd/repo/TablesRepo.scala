package op.assessment.nwgrnd.repo

import akka.actor.{ Actor, ActorRef, Terminated }
import op.assessment.nwgrnd.ws.WsApi._

class TablesRepo extends Actor {

  private[this] var tables = Vector.empty[IdTable]
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
    case Subscribe => sendToSource(Subscribed(tables.toList))
    case Add(afterId, t) =>
      val table = IdTable(0, t.name, t.participants)
      tables = table +: tables
      sendToSource(Added(afterId, table))
    case 'sinkclose ⇒ context.stop(self)
    case Terminated(a) if source.contains(a) =>
      source = None
      context.stop(self)
  }

  private def sendToSource(res: TableResult): Unit = source.foreach(_ ! res)
}
