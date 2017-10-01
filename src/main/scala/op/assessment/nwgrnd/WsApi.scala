package op.assessment.nwgrnd

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.scaladsl.Flow
import op.assessment.nwgrnd.WsApi.{ ClientContext, Login, LoginSuccessful }
import spray.json._

import scala.concurrent.{ ExecutionContext, Future }

object WsApi {
  class ClientContext {
    @volatile var userName: Option[String] = None
  }

  object ClientContext {
    def unapply(arg: ClientContext): Option[String] = arg.userName
  }

  case class Login(name: String, pass: String)
  case object LoginFailed
  case class LoginSuccessful(userType: String)
}

trait JsonSupport extends SprayJsonSupport {
  import DefaultJsonProtocol._

  implicit val materializer: Materializer
  implicit val loginFormat = jsonFormat(Login, "username", "password")
  implicit val loginSuccessful = jsonFormat(LoginSuccessful, "user_type")

  def parse(in: String): Future[Login] = {
    val json = in.parseJson.asJsObject
    val payload = JsObject(json.fields - "$type")
    json.fields("$type").convertTo[String].toLowerCase match {
      case "login" => Unmarshal(payload.compactPrint).to[Login]
    }
  } //Unmarshal(rsp.entity).to[T]//in.parseJson.convertTo[Login]

}

trait WsApi extends JsonSupport {
  import akka.http.scaladsl.server.Directives._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  import system.dispatcher

  val route: Route = path("ws-api") {
    handleWebSocketMessages(authHandler)
  }

  def auth(name: String, password: String): Boolean = {
    name == "user1234" && password == "password1234"
  }

  def authHandler: Flow[Message, Message, Any] =
    Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(2)(in => in.runFold("")(_ + _).flatMap(parse))
      .statefulMapConcat(() ⇒ {
        val context = new ClientContext
        m: Login ⇒ (context → m) :: Nil
      })
      .mapConcat {
        case m @ (c: ClientContext, Login(name, pass)) if auth(name, pass) =>
          c.userName = Some(name)
          m :: Nil

        case m @ (c: ClientContext, Login(name, pass)) => m :: Nil
      }
      .collect {
        case (ClientContext(userName), Login(_, _)) ⇒ LoginSuccessful("admin")
      }
      .mapAsync(2)(out ⇒ Future(TextMessage({
        val $type = JsObject("$type" -> JsString("login_successful"))
        val json = out.toJson.asJsObject
        val typed = $type.fields ++ json.fields
        JsObject(typed).compactPrint
      })))
}
