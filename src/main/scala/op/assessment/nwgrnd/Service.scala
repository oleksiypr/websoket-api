package op.assessment.nwgrnd

import op.assessment.nwgrnd.Security.{ Admin, Principal, User }

trait Service {
  val security: Security
}

trait Security {
  def auth(name: String, password: String): Option[Principal]
}

object Security {
  abstract class Principal(name: String, val role: String)
  case class User(name: String) extends Principal(name, "user")
  case class Admin(name: String) extends Principal(name, "admin")
}

trait SimpleService extends Service {
  val security: Security = {
    case ("user", "password-user") => Some(User("user"))
    case ("admin", "password-admin") => Some(Admin("admin"))
    case (_, _) => None
  }
}