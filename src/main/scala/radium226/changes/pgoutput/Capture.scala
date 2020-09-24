package radium226.changes.pgoutput

import cats.effect.{Blocker, ContextShift, Sync}

import radium226.changes.Change
import radium226.changes.pgoutput.protocol.{Message, Submessage}
import radium226.changes.pgoutput.reader.TupleDataReader
import radium226.changes.pgoutput.protocol.codec.{message => messageCodec}

import fs2._
import fs2.io.readInputStream

import scodec.{Attempt, DecodeResult}
import scodec.Err.{General, InsufficientBits}
import scodec.bits.BitVector

import cats.implicits._


object Capture {

  val GeneralMessagesToIgnore = List(
    "Insufficient number of elements",
    "Does not contain a 'NUL' termination byte"
  )

  def messages[F[_]: RaiseThrowable]: Pipe[F, Byte, Message] = { stream =>
    def go(leftStream: Stream[F, BitVector], leftBits: BitVector, first: Boolean): Pull[F, Message, Unit] = {
      def moveOn() = leftStream.pull.uncons1.flatMap({
        case Some((rightBits, rightStream)) =>
          go(rightStream, leftBits ++ rightBits, first)

        case None =>
          Pull.done
      })

      val bitsToDecode = leftBits.toByteVector.drop(if (first) 0 else 1).toBitVector
      messageCodec.decode(bitsToDecode) match {
        case Attempt.Successful(DecodeResult(message, remainingBits)) =>
          Pull.output1[F, Message](message) *> go(leftStream, remainingBits, false)

        case Attempt.Failure(InsufficientBits(_, _, _)) =>
          moveOn()

        case Attempt.Failure(General(message, _)) if GeneralMessagesToIgnore.exists(message.contains(_)) =>
          moveOn()

        case Attempt.Failure(cause) =>
          Pull.raiseError[F](new Exception(s"${cause}"))
      }
    }

    go(stream.chunks.map(_.toBitVector), BitVector.empty, true).stream
  }

  def changes[F[_]: RaiseThrowable, T](implicit tupleDataReaderForT: TupleDataReader[T]): Pipe[F, Message, Change[T]] = { messages =>
    messages
      .flatMap({
        case Message.Insert(_, newTupleData) =>
          tupleDataReaderForT
            .read(newTupleData)
            .map({ newValue =>
              Change.Insert(newValue)
            })
            .fold({ throwable =>
              Stream.raiseError[F](throwable)
            }, { insert =>
              Stream.emit[F, Change[T]](insert)
            })

        case Message.Update(_, Submessage.Old(oldTupleData), newTupleData) =>
          (for {
            oldValue <- tupleDataReaderForT.read(oldTupleData)
            newValue <- tupleDataReaderForT.read(newTupleData)
          } yield Change.Update(oldValue, newValue))
            .fold({ throwable =>
              Stream.raiseError[F](throwable)
            }, { update =>
              Stream.emit[F, Change[T]](update)
            })

        case Message.Update(_, _, _) =>
          Stream.raiseError[F](new Exception("You should configure your table with REPLICAS FULL"))

        case _ =>
          Stream.empty[F]
      })
  }

  def receive[F[_]: Sync: ContextShift](config: CaptureConfig): Stream[F, Byte] = {
    (for {
      blocker <- Stream.resource[F, Blocker](Blocker[F])
      process <- Stream.bracket[F, Process](F.delay({
        new ProcessBuilder()
          .command("pg_recvlogical",
            "-d", s"${config.database}",
            "-U", s"${config.user}",
            "-h", s"${config.host}",
            "-p", s"${config.port}",
            s"--slot=${config.slot}",
            "--file=-",
            "--no-loop",
            "--option=proto_version=1",
            s"--option=publication_names=${config.publications.mkString(",")}",
            "--plugin=pgoutput",
            "--start")
          .start()
      }))({ process =>
        F.delay(process.destroy())
      })
    } yield (blocker, process)).flatMap({ case (blocker, process) =>
      readInputStream(F.delay(process.getInputStream), 512, blocker)
    })
  }

  def capture[F[_]: Sync: ContextShift, T: TupleDataReader](config: CaptureConfig): Stream[F, Change[T]] = {
    receive[F](config)
      .through(messages[F])
      .through(changes[F, T])
  }

}
