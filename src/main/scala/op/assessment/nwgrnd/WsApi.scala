package op.assessment.nwgrnd

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }

trait WsApi {
  import akka.http.scaladsl.server.Directives._

  implicit val materializer: ActorMaterializer

  val route: Route = path("greeter") {
    handleWebSocketMessages(greeter)
  }

  def greeter: Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage =>
        TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }
}
