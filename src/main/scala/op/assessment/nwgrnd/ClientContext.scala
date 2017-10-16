package op.assessment.nwgrnd

import op.assessment.nwgrnd.ClientContext._
import op.assessment.nwgrnd.WsApi._

object ClientContext {

  private type Mapping = PartialFunction[TableRelated, Expectation]

  def unapply(arg: ClientContext): Option[Principal] = arg.principal

  private sealed trait Expectation
  private case class UpdateExpectation(id: Int) extends Expectation
  private case class RemovalExpectation(id: Int) extends Expectation

  abstract class Principal(name: String, val role: String)
  case class User(name: String) extends Principal(name, "user")
  case class Admin(name: String) extends Principal(name, "admin")
}

class ClientContext {

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

  private[this] val mapping: Mapping = {
    case Update(table) => UpdateExpectation(table.id)
    case Remove(id) => RemovalExpectation(id)
    case UpdateFailed(id) => UpdateExpectation(id)
    case RemovalFailed(id) => RemovalExpectation(id)
    case Updated(table) => UpdateExpectation(table.id)
    case Removed(id) => RemovalExpectation(id)
  }
}

trait Security {
  def auth(name: String, password: String): Option[Principal]
}

trait Service {
  val security: Security
}

trait SimpleService extends Service {
  val security: Security = {
    case ("user", "password-user") => Some(User("user"))
    case ("admin", "password-admin") => Some(Admin("admin"))
    case (_, _) => None
  }
}
