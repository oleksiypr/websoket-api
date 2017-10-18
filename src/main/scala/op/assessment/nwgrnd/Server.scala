package op.assessment.nwgrnd

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import op.assessment.nwgrnd.repo.TablesRepo
import op.assessment.nwgrnd.ws.{ SimpleService, WsApi }
import scala.concurrent.Future
import scala.io.StdIn

object Server extends App with WsApi with SimpleService {

  implicit val system = ActorSystem("TablesServerSystem")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val tablesRepo: ActorRef = system.actorOf(Props[TablesRepo])

  val serverBindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(route, "localhost", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")

  StdIn.readLine()

  serverBindingFuture
    .flatMap(_.unbind())
    .onComplete { done =>
      done.failed.map { ex => ex.printStackTrace() }
      system.terminate()
    }
}
