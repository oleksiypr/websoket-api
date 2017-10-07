package op.assessment.nwgrnd

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.Materializer
import op.assessment.nwgrnd.WsApi._
import spray.json._

trait JsonSupport extends SprayJsonSupport {
  import DefaultJsonProtocol._

  type Format[T] = RootJsonFormat[T]

  implicit val materializer: Materializer

  implicit val loginFormat: Format[Login] = jsonFormat(Login, "username", "password")
  implicit val loginSuccessfulFormat: Format[LoginSuccessful] = jsonFormat(LoginSuccessful, "user_type")
  implicit val pingFormat: Format[Ping] = jsonFormat(Ping, "seq")
  implicit val pongFormat: Format[Pong] = jsonFormat(Pong, "seq")
  implicit val loginFailedWriter: JsonWriter[LoginFailed.type] = _ => JsObject.empty
  implicit val tableFormat: Format[Table] = jsonFormat(Table, "id", "name", "participants")
  implicit val tablesFormat: Format[Tables] = jsonFormat(Tables, "tables")

  def unmarshal(in: String): Incoming = {
    val json = in.parseJson.asJsObject
    val payload = JsObject(json.fields - "$type")
    json.fields("$type").convertTo[String].toLowerCase match {
      case "login" => payload.convertTo[Login]
      case "ping" => payload.convertTo[Ping]
      case "subscribe_tables" => Subscribe
    }
  }

  def marshal(out: Outcoming): String = out match {
    case out: LoginFailed.type => marshal(out, "login_failed")
    case out: LoginSuccessful => marshal(out, "login_successful")
    case out: Pong => marshal(out, "pong")
    case out: Tables => marshal(out, "table_list")
  }

  def marshal[T <: Outcoming: JsonWriter](out: T, $type: String): String = {
    JsObject(
      JsObject("$type" -> JsString($type)).fields ++
        out.toJson.asJsObject.fields
    ).compactPrint
  }
}
