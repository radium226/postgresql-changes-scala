package radium226.changes

import java.nio.charset.StandardCharsets

import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import radium226.changes.pgoutput.protocol.{Message, Submessage, TupleData, Value}
import scodec.Attempt
import scodec.bits._

import scala.io.Source

abstract class AbstractSpec extends AnyFlatSpec with Matchers {

  protected def lines(bytes: Array[Byte]): List[Array[Byte]] = {
    bytes.indexOf('\n') match {
      case -1 =>
        if (bytes.isEmpty) List.empty else List(bytes)

      case n =>
        val (line, remainingBytes) = bytes.splitAt(n)
        line +: lines(remainingBytes.drop(1))
    }
  }

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
      case message @ Message.Insert(_, _) =>
        message

      case _ =>
        fail()
    }

    def update = message match {
      case message @ Message.Update(_, _, _) =>
        message

      case _ =>
        fail()
    }

    def delete = message match {
      case message @ Message.Delete(_, _) =>
        message

      case _ =>
        fail()
    }

  }

  implicit class SubmessageOps(submessage: Submessage) {

    def old = submessage match {
      case submessage @ Submessage.Old(_) =>
        submessage

      case _ =>
        fail()
    }

  }

  def withResource(resourceName: String)(block: List[BitVector] => Assertion): Assertion = {
    val resourceInputStream = getClass.getClassLoader.getResourceAsStream(resourceName)
    val bytes = LazyList.continually(resourceInputStream.read).takeWhile(_ != -1).map(_.toByte).toArray

    block(lines(bytes).map(BitVector(_)))
  }

}
