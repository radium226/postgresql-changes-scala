package radium226.changes.pgoutput
package protocol


sealed trait Submessage

object Submessage {

    case object Nothing extends Submessage

    case class Key(tupleData: TupleData) extends Submessage

    case class Old(tupleData: TupleData) extends Submessage

}