package op.assessment.nwgrnd

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.Materializer
import op.assessment.nwgrnd.WsApi._
import spray.json._

trait JsonSupport extends SprayJsonSupport {

  import DefaultJsonProtocol._
  implicit val materializer: Materializer
  type Format[T] = RootJsonFormat[T]

  implicit val loginFormat: Format[Login] = jsonFormat(Login, "username", "password")
  implicit val loginSuccessfulFormat: Format[LoginSuccessful] = jsonFormat(LoginSuccessful, "user_type")
  implicit val pingFormat: Format[Ping] = jsonFormat(Ping, "seq")
  implicit val pongFormat: Format[Pong] = jsonFormat(Pong, "seq")
  implicit val loginFailedWriter: JsonWriter[LoginFailed.type] = _ => JsObject.empty
  implicit val tableFormat: Format[Table] = jsonFormat(Table, "id", "name", "participants")
  implicit val tablesFormat: Format[Tables] = jsonFormat(Tables, "tables")

  implicit val updateFormat: Format[Update] = jsonFormat(Update, "table")
  implicit val updatedFormat: Format[Updated] = jsonFormat(Updated, "table")
  implicit val removeFormat: Format[Remove] = jsonFormat(Remove, "id")
  implicit val removedFormat: Format[Removed] = jsonFormat(Removed, "id")

  def unmarshal(in: String): Incoming = {
    val json = in.parseJson.asJsObject
    val payload = JsObject(json.fields - "$type")
    json.fields("$type").convertTo[String].toLowerCase match {
      case "login" => payload.convertTo[Login]
      case "ping" => payload.convertTo[Ping]
      case "subscribe_tables" => Subscribe
      case "unsubscribe_tables" => Unsubscribe
      case "update_table" => payload.convertTo[Update]
      case "remove_table" => payload.convertTo[Remove]
    }
  }

  def marshal(out: Outcoming): String = out match {
    case out: LoginFailed.type => marshal(out, "login_failed")
    case out: LoginSuccessful => marshal(out, "login_successful")
    case out: Pong => marshal(out, "pong")
    case out: Tables => marshal(out, "table_list")
    case out: Updated => marshal(out, "table_updated")
    case out: Removed => marshal(out, "table_removed")
  }

  def marshal[T <: Outcoming: JsonWriter](out: T, $type: String): String = {
    JsObject(
      JsObject("$type" -> JsString($type)).fields ++
        out.toJson.asJsObject.fields
    ).compactPrint
  }
}
