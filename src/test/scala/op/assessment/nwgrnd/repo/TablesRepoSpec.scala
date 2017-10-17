package op.assessment.nwgrnd.repo

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import op.assessment.nwgrnd.ws.WsApi.{ Subscribe, Subscribed }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

class TablesRepoSpec(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("TablesRepoSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "TablesRepo" should {
    "wait for source actor and shut dount when done" in {
      val tablesRepo = watch(system.actorOf(Props[TablesRepo]))

      val sourceProbe = TestProbe()
      watch(sourceProbe.ref)

      tablesRepo ! Subscribe
      expectMsg('not_ready)

      tablesRepo ! ('income → sourceProbe.ref)
      sourceProbe.expectNoMsg()

      tablesRepo ! Subscribe
      sourceProbe.expectMsg(Subscribed(Nil))

      tablesRepo ! 'sinkclose

      expectTerminated(sourceProbe.ref)
      expectTerminated(tablesRepo)
    }
  }
}
