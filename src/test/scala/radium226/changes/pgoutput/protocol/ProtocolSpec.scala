package radium226.changes.pgoutput.protocol

import org.scalatest.Inspectors._
import radium226.test.{AbstractSpec, Protocol}


class ProtocolSpec extends AbstractSpec with Protocol {

  "All statements" should "begin with a Begin message and end with a Commit message" in forAll(List("INSERT", "UPDATE", "DELETE")) { statement =>
    withResource(s"${statement}.bin") { lines =>
      println(lines.map({ line => new String(line.toByteArray)}))
      codec.message.decodeValue(lines.head).successful shouldBe a[Message.Begin]
      codec.message.decodeValue(lines.last).successful shouldBe a[Message.Commit]
    }
  }

  "Protocol codec" should "be able to decode INSERT statements" in withResource("INSERT.bin") { lines =>
    val insert = codec.message.decodeValue(lines(2)).successful.insert
    insert.tupleData.toStringList should be(List("1", "Albert", "Einstein"))
  }

  "Protocol codec" should "be able to decode UPDATE statements" in withResource("UPDATE.bin") { lines =>
    val update = codec.message.decodeValue(lines(2)).successful.update
    update.submessage.old.tupleData.toStringList should be(List("1", "Albert", "Einstein"))
    update.newTupleData.toStringList should be(List("1", "Niels", "Bohr"))
  }

  "Codec" should "be able to decode DELETE statements" in withResource("DELETE.bin") { lines =>
    val delete = codec.message.decodeValue(lines(2)).successful.delete
    delete.submessage.old.tupleData.toStringList should be(List("1", "Niels", "Bohr"))
  }

}
