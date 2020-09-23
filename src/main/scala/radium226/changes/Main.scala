package radium226.changes

import cats._
import cats.effect._

import cats.implicits._
import cats.effect.implicits._

import fs2._
import fs2.io.file._
import java.nio.file.Paths

import scodec._
import scodec.codecs._
import scodec.stream._
import scodec.bits._

import parser.instances._

import radium226.changes.pgoutput.protocol._

// https://www.postgresql.org/docs/12/protocol-logicalrep-message-formats.html
object Main extends IOApp {

    override def run(args: List[String]): IO[ExitCode] = {
        Blocker[IO]
            .use({ blocker =>
                readAll[IO](Paths.get("./pgoutput.bin"), blocker, 128)
                    .split(_ == '\n')
                    .map(_.toBitVector)
                    .map({ bitVector => Codec[Message].decodeValue(bitVector) })
                    .evalTap({
                        case Attempt.Successful(Message.Insert(_, TupleData(values))) =>
                            values
                                .traverse({
                                    case Value.Text(byteVector) =>
                                        IO(println(new String(byteVector.toArray)))

                                    case _ =>
                                        IO.unit
                                })
                                .void

                        case _ =>
                            IO.unit
                     })
                    .evalTap({ attempt => 
                        IO(println(attempt)) 
                    })
                    .through(UpdatedValue[IO].of[Person])
                    .evalTap({ k => IO(println(k)) })
                    .compile
                    .drain
            })
            .as(ExitCode.Success)
    }

}