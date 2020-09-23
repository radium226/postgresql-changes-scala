package radium226.changes

import fs2._
import scodec.Attempt
import scodec.Attempt.Successful
import radium226.changes.parser.TupleDataParser
import scala.util.{Try, Success}

import radium226.changes.pgoutput.protocol._

object UpdatedValue {


    def apply[F[_]] = new UpdatedValue[F] {

        def of[T](implicit tupleDataParserForT: TupleDataParser[T]): Pipe[F, Attempt[Message], T] = { attempts =>
            attempts
                .collect({
                    case Successful(Message.Update(_, submessage, newTupleData)) =>
                        println(s"submessage=${submessage}")
                        tupleDataParserForT.parse(newTupleData)
                })
                .collect({
                    case Success(t) =>
                        t
                })
        }

    }


}

trait UpdatedValue[F[_]] {

    def of[T](implicit tupleDataParserForT: TupleDataParser[T]): Pipe[F, Attempt[Message], T]

}