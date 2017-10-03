package op.assessment.nwgrnd

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import op.assessment.nwgrnd.WsApi.ClientContext.Auth
import op.assessment.nwgrnd.WsApi._

object WsApi {
  class ClientContext {
    @volatile var auth: Option[Auth] = None
  }

  object ClientContext {
    def unapply(arg: ClientContext): Option[Auth] = arg.auth
    case class Auth(userName: String, role: String)
  }

  abstract class Incoming(val $type: String)
  case class Login(name: String, pass: String) extends Incoming("login")
  case class Ping(seq: Int) extends Incoming("ping")

  trait Outcoming
  case object LoginFailed extends Outcoming
  case class LoginSuccessful(userType: String) extends Outcoming
  case class Pong(seq: Int) extends Outcoming
}

trait WsApi extends JsonSupport {
  import akka.http.scaladsl.server.Directives._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  import system.dispatcher

  val route: Route = path("ws-api") {
    handleWebSocketMessages(authHandler)
  }

  def auth(name: String, password: String): Option[Auth] = {
    (name, password) match {
      case ("user", "password-user") => Some(Auth(name, "user"))
      case ("admin", "password-admin") => Some(Auth(name, "admin"))
      case _ => None
    }
  }

  def authHandler: Flow[Message, Message, Any] =
    Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(2)(in => in.runFold("")(_ + _).map(unmarshal))
      .statefulMapConcat(() ⇒ {
        val context = new ClientContext
        m ⇒ (context → m) :: Nil
      })
      .mapConcat {
        case m @ (c: ClientContext, Login(name, pass)) =>
          c.auth = auth(name, pass)
          m :: Nil
        case m @ (_: ClientContext, _) => m :: Nil

      }
      .collect {
        case (ClientContext(auth), Login(_, _)) ⇒ LoginSuccessful(auth.role)
        case (_: ClientContext, Login(_, _)) => LoginFailed
        case (c: ClientContext, Ping(seq)) if c.auth.nonEmpty => Pong(seq)
      }
      .map(out => TextMessage(marshal(out)))
}
