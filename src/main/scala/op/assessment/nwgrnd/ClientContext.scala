package op.assessment.nwgrnd

import op.assessment.nwgrnd.ClientContext.{ Admin, Principal, User }

object ClientContext {

  def unapply(arg: ClientContext): Option[Principal] = arg.principal

  abstract class Principal(name: String, val role: String)
  case class User(name: String) extends Principal(name, "user")
  case class Admin(name: String) extends Principal(name, "admin")
}

class ClientContext {
  @volatile var isSubscribed: Boolean = false
  @volatile var principal: Option[Principal] = None
  def isAuthorized: Boolean = principal.exists(_.role == "admin")
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

