package radium226.changes.pgoutput
package reader

import scala.util._
import radium226.changes.pgoutput.protocol._
import java.nio.charset.StandardCharsets


trait ValueReader[T] {

    def read(value: Value): Try[T]

}


object ValueReader {

    def instance[T](f: Value => Try[T]): ValueReader[T] = new ValueReader[T] {

        override def read(value: Value): Try[T] = f(value)

    }

    def constant[T](c: T) = instance { _ => Try(c) }

    def apply[T](implicit valueReaderForT: ValueReader[T]) = valueReaderForT

}


trait ValueReaderInstances {

    implicit val valueReaderForDouble: ValueReader[Double] = ValueReader.instance {
        case Value.Text(byteVector) =>
            for {
                string <- ValueReader[String].read(Value.Text(byteVector))
                double <- Try(java.lang.Double.parseDouble(string))
            } yield double


        case value =>
            Failure(new Exception(s"Unable to convert ${value}"))
    }

    implicit val valueReaderForString: ValueReader[String] = ValueReader.instance {
        case Value.Text(byteVector) =>
            Try(s"${StandardCharsets.UTF_8.decode(byteVector.toByteBuffer).toString}")
            
        case value =>
            Failure(new Exception(s"Unable to convert ${value}"))
    }

    implicit val valueReaderForLong: ValueReader[Long] = ValueReader.instance {
        case Value.Text(byteVector) =>
            for {
                string <- ValueReader[String].read(Value.Text(byteVector))
                long <- Try(java.lang.Long.parseLong(string))
            } yield long
            
        case value =>
            Failure(new Exception(s"Unable to convert ${value}")) 
    }

}