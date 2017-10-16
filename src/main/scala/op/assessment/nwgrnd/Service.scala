package op.assessment.nwgrnd

import op.assessment.nwgrnd.ClientContext.{ Admin, Principal, User }

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