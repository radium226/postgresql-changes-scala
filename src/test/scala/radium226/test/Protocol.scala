package radium226.test

import java.nio.charset.StandardCharsets

import org.scalatest.Assertions
import radium226.changes.pgoutput.protocol.{Message, Submessage, TupleData, Value}
import scodec.Attempt

trait Protocol {
  self: Assertions =>

  implicit class TupleDataOps(tupleData: TupleData) {

    def toStringList: List[String] = tupleData.values.collect({
      case Value.Text(byteVector) =>
        StandardCharsets.UTF_8.decode(byteVector.toByteBuffer).toString
    })

  }

  implicit class AttemptOps[A](attempt: Attempt[A]) {

    def successful: A = attempt.fold({ failed => fail(failed.messageWithContext) }, identity)

  }

  implicit class MessageOps(message: Message) {

    def insert = message match {
      case message@Message.Insert(_, _) =>
        message

      case _ =>
        fail()
    }

    def update = message match {
      case message@Message.Update(_, _, _) =>
        message

      case _ =>
        fail()
    }

    def delete = message match {
      case message@Message.Delete(_, _) =>
        message

      case _ =>
        fail()
    }

  }

  implicit class SubmessageOps(submessage: Submessage) {

    def old = submessage match {
      case submessage@Submessage.Old(_) =>
        submessage

      case _ =>
        fail()
    }

  }

}
