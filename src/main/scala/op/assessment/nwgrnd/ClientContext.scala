package op.assessment.nwgrnd

import op.assessment.nwgrnd.ClientContext.{ Admin, Principal, User }

object ClientContext {

  def unapply(arg: ClientContext): Option[Principal] = arg.principal

  abstract class Principal(name: String, val role: String)
  case class User(name: String) extends Principal(name, "user")
  case class Admin(name: String) extends Principal(name, "admin")
}

class ClientContext {
  @volatile
  var principal: Option[Principal] = None
}

trait AuthService {
  def auth(name: String, password: String): Option[Principal]
}

trait Security {
  val authService: AuthService
}

trait SimpleSecurity extends Security {
  val authService: AuthService = {
    case ("user", "password-user") => Some(User("user"))
    case ("admin", "password-admin") => Some(Admin("admin"))
    case (_, _) => None
  }
}

