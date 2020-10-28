package radium226.test

import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scodec.bits._

abstract class AbstractSpec extends AnyFlatSpec with Matchers with RunIO {

  protected def lines(bytes: Array[Byte]): List[Array[Byte]] = {
    bytes.indexOf('\n') match {
      case -1 =>
        if (bytes.isEmpty) List.empty else List(bytes)

      case n =>
        val (line, remainingBytes) = bytes.splitAt(n)
        line +: lines(remainingBytes.drop(1))
    }
  }


  def withResource(resourceName: String)(block: List[BitVector] => Assertion): Assertion = {
    val resourceInputStream = getClass.getClassLoader.getResourceAsStream(resourceName)
    val bytes = LazyList.continually(resourceInputStream.read).takeWhile(_ != -1).map(_.toByte).toArray

    block(lines(bytes).map(BitVector(_)))
  }

}
