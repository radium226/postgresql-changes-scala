package radium226.changes.pgoutput
package protocol

import scodec.Codec

import java.time.Instant

import radium226.changes.pgoutput.protocol.codec.message


sealed trait Message

object Message {

    implicit val codec: Codec[Message] = message

    case class Begin(lsn: LogSequenceNumber, commitInstant: Instant, xid: TransactionID) extends Message

    case class Commit(flags: Flags, commitLSN: LogSequenceNumber, transactionEndLSN: LogSequenceNumber, commitTimestamp: Instant) extends Message

    case class Insert(relationID: RelationID, tupleData: TupleData) extends Message

    case class Update(relationID: RelationID, submessage: Submessage, newTupleData: TupleData) extends Message

}