package op.assessment.nwgrnd.repo

import op.assessment.nwgrnd.ws.WsApi._

private[repo] case class TablesState(tables: Vector[Table] = Vector.empty) {

  def apply(cmd: TableCommand): (TableResult, TablesState) = {
    cmd match {
      case Subscribe => (Subscribed(indexed.toList), this)
      case Add(afterId, t) =>
        val (fore, aft) = tables.splitAt(afterId + 1)
        val id = fore.size
        val table = Table(t.name, t.participants)
        val res = IdTable(id, t.name, t.participants)
        (Added(afterId, res), copy(tables = (fore :+ table) ++ aft))
      case Update(t) if notFound(t.id) => (UpdateFailed(t.id), this)
      case Update(t) => (Updated(t), copy(tables = updated(t)))
      case Remove(id) if notFound(id) => (RemovalFailed(id), this)
      case Remove(id) => (Removed(id), copy(tables = removed(id)))
    }
  }

  private def indexed = for {
    (t, id) <- tables.zipWithIndex
  } yield IdTable(id, t.name, t.participants)

  private def notFound(id: Int): Boolean = id < 0 || id >= tables.size

  private def updated(t: IdTable) = {
    tables.updated(t.id, Table(t.name, t.participants))
  }

  private def removed(id: Int) = {
    tables.take(id) ++ tables.drop(id + 1)
  }
}
