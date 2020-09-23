package radium226.changes

import radium226.changes.pgoutput.protocol._
import radium226.changes.pgoutput.reader._

import scala.util._

import fs2._
import cats.effect._


trait Capture[T] {

    def pipe[F[_]: ConcurrentEffect]: Pipe[F, Message, Change[T]]

}

object Capture {

    def apply[T](implicit tupleDataReaderForT: TupleDataReader[T]) = new Capture[T] {

        override def pipe[F[_]: ConcurrentEffect]: Pipe[F, Message, Change[T]] = { messages =>
            messages.flatMap({
                case Message.Insert(_, newTupleData) =>
                    tupleDataReaderForT
                        .read(newTupleData)
                        .map({ newValue =>
                            Change.Insert(newValue)
                        })
                        .fold({ throwable =>
                            Stream.raiseError(throwable)
                        }, { insert =>
                            Stream.emit(insert)
                        })

                case Message.Update(_, Submessage.Old(oldTupleData), newTupleData) =>
                    (for {
                        oldValue <- tupleDataReaderForT.read(oldTupleData)
                        newValue <- tupleDataReaderForT.read(newTupleData)
                    } yield Change.Update(oldValue, newValue))
                        .fold({ throwable =>
                            Stream.raiseError(throwable)
                        }, { update => 
                             Stream.emit(update)
                        })

                case Message.Update(_, _, _) =>
                    Stream.raiseError(new Exception("You should configure your table with REPLICAS FULL"))

                case _ =>
                    Stream.empty[F]
            })            
        }

    }

}