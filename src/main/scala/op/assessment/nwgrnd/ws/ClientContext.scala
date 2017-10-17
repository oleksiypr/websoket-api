package op.assessment.nwgrnd.ws

import op.assessment.nwgrnd.ws.ClientContext._
import op.assessment.nwgrnd.ws.Security.Principal
import op.assessment.nwgrnd.ws.WsApi._

object ClientContext {

  private sealed trait Expectation
  private case class UpdateExpectation(id: Int) extends Expectation
  private case class RemovalExpectation(id: Int) extends Expectation

  private val mapping: PartialFunction[TableRelated, Expectation] = {
    case Update(table) => UpdateExpectation(table.id)
    case Remove(id) => RemovalExpectation(id)
    case UpdateFailed(id) => UpdateExpectation(id)
    case RemovalFailed(id) => RemovalExpectation(id)
    case Updated(table) => UpdateExpectation(table.id)
    case Removed(id) => RemovalExpectation(id)
  }

  def unapply(arg: ClientContext): Option[Principal] = arg.principal
}

class ClientContext private[ws] () {

  private[this] var expectations =
    Map.empty[Expectation, Int].withDefaultValue(0)

  @volatile var isSubscribed: Boolean = false
  @volatile var principal: Option[Principal] = None

  def isAuthorized: Boolean = principal.exists(_.role == "admin")

  def becomeExpecting(tc: TableCommand): Unit =
    if (mapping.isDefinedAt(tc)) {
      val en = mapping(tc)
      expectations += en -> (expectations(en) + 1)
    }

  def unbecomeExpecting(res: TableResult): Unit =
    if (mapping.isDefinedAt(res)) {
      val en = mapping(res)
      if (expectations(en) == 1) expectations -= en
      else expectations += en -> (expectations(en) - 1)
    }

  def isExpecting(res: TableResult): Boolean = {
    mapping.isDefinedAt(res) &&
      expectations(mapping(res)) > 0
  }
}

