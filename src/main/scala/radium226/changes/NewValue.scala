package radium226.changes

import fs2._
import scodec.Attempt
import scodec.Attempt.Successful
import radium226.changes.parser.TupleDataParser
import scala.util.{Try, Success}

import radium226.changes.pgoutput.protocol._


object NewValue {


    def apply[F[_]] = new NewValue[F] {

        def of[T](implicit tupleDataParserForT: TupleDataParser[T]): Pipe[F, Attempt[Message], T] = { attempts =>
            attempts
                .collect({
                    case Successful(Message.Insert(_, tupleData)) =>
                        tupleDataParserForT.parse(tupleData)
                })
                .collect({
                    case Success(t) =>
                        t
                })
        }

    }


}

trait NewValue[F[_]] {

    def of[T](implicit tupleDataParserForT: TupleDataParser[T]): Pipe[F, Attempt[Message], T]

}