package op.assessment.nwgrnd

import op.assessment.nwgrnd.ClientContext.Principal

object ClientContext {

  def unapply(arg: ClientContext): Option[Principal] = arg.auth

  abstract class Principal(name: String, val role: String)
  case class User(name: String) extends Principal(name, "user")
  case class Admin(name: String) extends Principal(name, "admin")
}

class ClientContext {
  @volatile var auth: Option[Principal] = None
}
