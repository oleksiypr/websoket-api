package op.assessment.nwgrnd.repo

import op.assessment.nwgrnd.ws.WsApi._

private[repo] case class TablesState(
    tables: Vector[IdTable] = Vector.empty[IdTable]
) {

  def apply(cmd: TableCommand): (TableResult, TablesState) = {
    cmd match {
      case Subscribe => (Subscribed(tables.toList), this)
      case Add(afterId, t) =>
        val table = IdTable(0, t.name, t.participants)
        (Added(afterId, table), copy(tables = table +: tables))
    }
  }
}
