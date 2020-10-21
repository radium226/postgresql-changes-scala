package radium226.changes.pgoutput
package protocol

import scodec.bits.ByteVector

//<<< value-sealed-trait-definition
sealed trait Value

object Value {

    case object Null extends Value

    case class Text(value: ByteVector) extends Value

    case object Toasted extends Value

}
//>>>