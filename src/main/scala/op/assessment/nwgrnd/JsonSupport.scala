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
  implicit val notAuthorizedWriter: JsonWriter[NotAuthorized.type] = _ => JsObject.empty

  implicit val idTableFormat: Format[IdTable] = jsonFormat(IdTable, "id", "name", "participants")
  implicit val tableFormat: Format[Table] = jsonFormat(Table, "name", "participants")
  implicit val subscribedFormat: Format[Subscribed] = jsonFormat(Subscribed, "tables")

  implicit val addFormat: Format[Add] = jsonFormat(Add, "after_id", "table")
  implicit val updateFormat: Format[Update] = jsonFormat(Update, "table")
  implicit val remove: Format[Remove] = jsonFormat(Remove, "id")

  implicit val addedFormat: Format[Added] = jsonFormat(Added, "after_id", "table")
  implicit val updatedFormat: Format[Updated] = jsonFormat(Updated, "table")
  implicit val removedFormat: Format[Removed] = jsonFormat(Removed, "id")

  def unmarshal(in: String): WsIn = {
    val json = in.parseJson.asJsObject
    val payload = JsObject(json.fields - "$type")
    json.fields("$type").convertTo[String].toLowerCase match {
      case "login" => payload.convertTo[Login]
      case "ping" => payload.convertTo[Ping]
      case "subscribe_tables" => Subscribe
      case "unsubscribe_tables" => Unsubscribe
      case "add_table" => payload.convertTo[Add]
      case "update_table" => payload.convertTo[Update]
      case "remove_table" => payload.convertTo[Remove]
    }
  }

  def marshal(out: WsOut): String = out match {
    case out: LoginFailed.type => marshal(out, "login_failed")
    case out: NotAuthorized.type => marshal(out, "not_authorized")
    case out: LoginSuccessful => marshal(out, "login_successful")
    case out: Pong => marshal(out, "pong")
    case out: Subscribed => marshal(out, "table_list")
    case out: Added => marshal(out, "table_added")
    case out: Updated => marshal(out, "table_updated")
    case out: Removed => marshal(out, "table_removed")
  }

  def marshal[T <: WsOut: JsonWriter](out: T, $type: String): String = {
    JsObject(
      JsObject("$type" -> JsString($type)).fields ++
        out.toJson.asJsObject.fields
    ).compactPrint
  }
}
