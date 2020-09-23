package radium226.changes.pgoutput
package reader

import scala.util._
import radium226.changes.pgoutput.protocol._
import shapeless._


trait TupleDataReader[T] {

    def read(tupleData: TupleData): Try[T]

}


object TupleDataReader {

    def instance[T](f: TupleData => Try[T]): TupleDataReader[T] = new TupleDataReader[T] {

        override def read(tupleData: TupleData): Try[T] = f(tupleData)

    }

    def constant[T](c: T) = instance { _ => Try(c) }

    def apply[T](implicit tupleDataReaderForT: TupleDataReader[T]) = tupleDataReaderForT

}


trait TupleDataReaderInstances {

    implicit val tupleDataReaderForHNil: TupleDataReader[HNil] = TupleDataReader.constant(HNil)

    implicit def tupleDataReaderForHCons[H, T <: HList](implicit 
        valueReaderForH: ValueReader[H],
        tupleDataReaderForT: TupleDataReader[T]    
    ): TupleDataReader[H :: T] = TupleDataReader.instance { tupleData =>
        for {
            h <- valueReaderForH.read(tupleData.values.head)
            t <- tupleDataReaderForT.read(TupleData(tupleData.values.tail))
        } yield h :: t
    }

    implicit def tupleDataReaderForHList[T, ReprOfT <: HList](implicit 
        generic: Generic.Aux[T, ReprOfT], 
        tupleDataReaderForReprOfT: TupleDataReader[ReprOfT]
    ): TupleDataReader[T] = TupleDataReader.instance { tupleData =>
        tupleDataReaderForReprOfT
            .read(tupleData)
            .map({ reprOfT =>
                generic.from(reprOfT)
            })
    }

}