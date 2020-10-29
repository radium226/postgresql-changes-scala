package radium226.changes.pgoutput

import cats.effect.{Blocker, Concurrent, ContextShift, Sync}
import radium226.changes.Change
import radium226.changes.pgoutput.protocol.{Message, RelationID, RelationName, Submessage, TransactionID}
import radium226.changes.pgoutput.reader.TupleDataReader
import radium226.changes.pgoutput.protocol.codec.{message => messageCodec}
import fs2._
import fs2.io.readInputStream
import scodec.{Attempt, DecodeResult}
import scodec.Err.{General, InsufficientBits}
import scodec.bits.BitVector
import cats.implicits._

import scala.util.{Failure, Success}


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

        case Message.Delete(_, Submessage.Old(oldTupleData)) =>
          tupleDataReaderForT
            .read(oldTupleData)
            .map({ oldValue =>
              Change.Delete[T](oldValue)
            })
            .fold({ throwable =>
              Stream.raiseError[F](throwable)
            }, { insert =>
              Stream.emit[F, Change[T]](insert)
            })

        case Message.Update(_, _, _) | Message.Delete(_, _) =>
          Stream.raiseError[F](new Exception("You should configure your table with REPLICAS FULL"))

        case _ =>
          Stream.empty[F]
      })
  }

//<<< receive-method
  def receive[F[_]: Sync: ContextShift: Concurrent](config: CaptureConfig): Stream[F, Byte] = {
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
            "--status-interval=1",
            "--fsync-interval=1",
            "--file=-",
            "--no-loop",
            "-v",
            "--option=proto_version=1",
            s"--option=publication_names=${config.publications.mkString(",")}",
            "--plugin=pgoutput",
            "--create",
            "--if-not-exists",
            "--start")
          .start()
      }))({ process =>
        F.delay(process.destroy())
      })
    } yield (blocker, process)).flatMap({ case (blocker, process) =>
      readInputStream(F.delay(process.getInputStream), 512, blocker)
        .concurrently(readInputStream(F.delay(process.getErrorStream), 512, blocker).through(text.utf8Decode).showLines(System.err))
    })
  }
//>>>

  def captureChanges[F[_]: Sync: ContextShift: Concurrent, T: TupleDataReader](config: CaptureConfig): Stream[F, Change[T]] = {
    receive[F](config)
      .through(messages[F])
      .through(changes[F, T])
  }

  def captureTransactions[F[_]: Sync: ContextShift: Concurrent](config: CaptureConfig, tupleDataReadersByRelationName: Map[RelationName, TupleDataReader[_]]) = {
    receive[F](config)
      .through(messages[F])
      .through(transactions[F](tupleDataReadersByRelationName))
  }

  def captureMessages[F[_]: Sync: ContextShift: Concurrent](config: CaptureConfig): Stream[F, Message] = {
    receive[F](config).through(messages[F])
  }

  def transactions[F[_]: RaiseThrowable](tupleDataReadersByRelationName: Map[RelationName, TupleDataReader[_]]): Pipe[F, Message, Transaction] = { messageStream =>
    def go(messageStream: Stream[F, Message], relations: List[Message.Relation], transactionOption: Option[Transaction]): Pull[F, Transaction, Unit] = {
      messageStream.pull.uncons1.flatMap({
        case Some((Message.Begin(_, _, transactionID), remainingMessageStream)) =>
          transactionOption match {
            case Some(_) =>
              Pull.raiseError(new Exception("A transaction is already in progress! "))

            case None =>
              go(remainingMessageStream, relations, Transaction.empty(transactionID).some)
          }

        case Some((Message.Commit(_, _, _), remainingMessageStream)) =>
          transactionOption match {
            case Some(transaction) =>
              for {
                _ <- Pull.output1(transaction)
                _ <- go(remainingMessageStream, relations, none[Transaction])
              } yield ()

            case None =>
              Pull.raiseError(new Exception("There is no transaction in progress"))
          }

        case Some((relation @ Message.Relation(_, _, _, _, _), remainingMessageStream)) =>
          go(remainingMessageStream, relations :+ relation, transactionOption)

        case Some((message @ RelationID(relationID), remainingMessageStream)) =>
          transactionOption match {
            case Some(transaction) =>
              (for {
                relation        <- relations.find(_.id == relationID)
                relationName     = relation.name
                tupleDataReader <- tupleDataReadersByRelationName.get(relationName)
              } yield tupleDataReader) match {
                case Some(tupleDataReader) =>
                  (message match {
                    case Message.Insert(_, newTupleData) =>
                      tupleDataReader
                        .read(newTupleData)
                        .map({ newValue =>
                          Change.Insert(newValue)
                        })

                    case Message.Update(_, Submessage.Old(oldTupleData), newTupleData) =>
                      for {
                        oldValue <- tupleDataReader.read(oldTupleData)
                        newValue <- tupleDataReader.read(newTupleData)
                      } yield Change.Update(oldValue, newValue)

                    case Message.Delete(_, Submessage.Old(oldTupleData)) =>
                      tupleDataReader
                        .read(oldTupleData)
                        .map({ oldValue =>
                          Change.Delete(oldValue)
                        })

                    case _ =>
                      Failure(new Exception("You should be configure your table with REPLICA FULL"))
                  }) match {
                    case Success(change) =>
                      go(remainingMessageStream, relations, (transaction :+ change).some)

                    case Failure(throwable) =>
                      Pull.raiseError(throwable)
                  }

                case None =>
                  Pull.raiseError(new Exception(s"Unable to find a tupleDataReader for the ${relationID} relation ID"))
              }

            case None =>
              Pull.raiseError(new Exception("There is no transaction in progress"))
          }

        case None =>
          Pull.done
      })
    }

    go(messageStream, List.empty[Message.Relation], none[Transaction]).stream
  }

}
