package radium226.changes.pgoutput
package protocol

import scodec.Codec

import java.time.Instant

import radium226.changes.pgoutput.protocol.codec.message

//<<< message-sealed-trait-definition
sealed trait Message

object Message {

    case class Begin(lsn: LogSequenceNumber, commitInstant: Instant, xid: TransactionID) extends Message

    case class Commit(commitLSN: LogSequenceNumber, transactionEndLSN: LogSequenceNumber, commitTimestamp: Instant) extends Message

    case class Insert(relationID: RelationID, tupleData: TupleData) extends Message

    case class Update(relationID: RelationID, submessage: Submessage, newTupleData: TupleData) extends Message

    case class Delete(relationId: RelationID, submessage: Submessage) extends Message

    case class Relation(id: RelationID, namespace: RelationNamespace, name: RelationName, replicaIdentitySetting: Int, columns: List[Column]) extends Message

}
//>>>