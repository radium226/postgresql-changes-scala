package radium226.changes.pgoutput

import radium226.changes.Change

case class Transaction(id: TransactionID, changes: List[Change[_]]) {

  def isEmpty: Boolean = changes.isEmpty

  def :+(change: Change[_]): Transaction = copy(changes = changes :+ change)

}

object Transaction {

  def empty(transactionID: TransactionID): Transaction = Transaction(transactionID, List.empty[Change[_]])

}
