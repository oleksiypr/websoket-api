package op.assessment.nwgrnd.repo

import op.assessment.nwgrnd.ws.WsApi._

private[repo] case class TablesState(
    tables: Vector[Table] = Vector.empty
) {

  def apply(cmd: TableCommand): (TableResult, TablesState) = {
    cmd match {
      case Subscribe => (Subscribed(indexed.toList), this)
      case Add(afterId, t) =>
        val (fore, aft) = tables.splitAt(afterId + 1)
        val id = fore.size
        val table = Table(t.name, t.participants)
        val res = IdTable(id, t.name, t.participants)
        (Added(afterId, res), copy(tables = (fore :+ table) ++ aft))
    }
  }

  private def indexed = for {
    (t, id) <- tables.zipWithIndex
  } yield IdTable(id, t.name, t.participants)
}
