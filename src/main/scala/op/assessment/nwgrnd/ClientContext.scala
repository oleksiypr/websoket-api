package op.assessment.nwgrnd

import op.assessment.nwgrnd.ClientContext._
import op.assessment.nwgrnd.WsApi._

object ClientContext {

  def unapply(arg: ClientContext): Option[Principal] = arg.principal

  private sealed trait Expectation
  private case class UpdateExpectation(id: Int) extends Expectation
  private case class RemovalExpectation(id: Int) extends Expectation

  abstract class Principal(name: String, val role: String)
  case class User(name: String) extends Principal(name, "user")
  case class Admin(name: String) extends Principal(name, "admin")
}

class ClientContext {

  private[this] var expectations = Map.empty[Expectation, Int].withDefaultValue(0)

  @volatile var isSubscribed: Boolean = false
  @volatile var principal: Option[Principal] = None

  def isAuthorized: Boolean = principal.exists(_.role == "admin")

  def becomeExpecting(tc: TableCommand): Unit = tc match {
    case Update(table) => becomeExpecting(UpdateExpectation(table.id))
    case Remove(id) => becomeExpecting(RemovalExpectation(id))
    case _ =>
  }

  def unbecomeExpecting(res: TableResult): Unit = res match {
    case UpdateFailed(id) => unbecomeExpecting(UpdateExpectation(id))
    case RemovalFailed(id) => unbecomeExpecting(RemovalExpectation(id))
    case Updated(table) => unbecomeExpecting(UpdateExpectation(table.id))
    case Removed(id) => unbecomeExpecting(RemovalExpectation(id))
    case _ =>
  }

  def isExpecting(res: TableResult): Boolean = res match {
    case UpdateFailed(id) => expectations(UpdateExpectation(id)) > 0
    case RemovalFailed(id) => expectations(RemovalExpectation(id)) > 0
    case Updated(table) => expectations(UpdateExpectation(table.id)) > 0
    case Removed(id) => expectations(RemovalExpectation(id)) > 0
    case _ => false
  }

  private def becomeExpecting(en: Expectation): Unit = {
    expectations += en -> (expectations(en) + 1)
  }

  private def unbecomeExpecting(en: Expectation): Unit = {
    if (expectations(en) == 1) expectations -= en
    else expectations += en -> (expectations(en) - 1)
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

