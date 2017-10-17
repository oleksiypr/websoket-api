package op.assessment.nwgrnd.repo

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import op.assessment.nwgrnd.ws.WsApi._
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
    "operate tables" in {
      val tablesRepo = watch(system.actorOf(Props[TablesRepo]))
      val sourceProbe = TestProbe()
      tablesRepo ! ('income → sourceProbe.ref)

      addTable(tablesRepo, afterId = -1, "A")
      addTable(tablesRepo, afterId = -1, "B")
      addTable(tablesRepo, afterId = -1, "C")

      expectAdded(sourceProbe, afterId = -1, 0, "A")
      expectAdded(sourceProbe, afterId = -1, 0, "B")
      expectAdded(sourceProbe, afterId = -1, 0, "C")

      tablesRepo ! Subscribe
      sourceProbe.expectMsg(
        Subscribed(List(
          IdTable(id = 0, name = "C", participants = 2),
          IdTable(id = 1, name = "B", participants = 2),
          IdTable(id = 2, name = "A", participants = 2)
        ))
      )

      addTable(tablesRepo, afterId = 1, "D")
      expectAdded(sourceProbe, afterId = 1, 2, "D")
    }
  }

  private def expectAdded(
    sourceProbe: TestProbe,
    afterId: Int,
    id: Int,
    name: String
  ): Any = {
    sourceProbe.expectMsg(Added(
      afterId, IdTable(id, name, participants = 2)
    ))
  }

  private def addTable(
    tablesRepo: ActorRef, afterId: Int, name: String
  ): Unit = {
    tablesRepo ! Add(afterId, Table(name = name, participants = 2))
  }
}
