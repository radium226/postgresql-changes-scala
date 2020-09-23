package radium226.changes

import scala.util.Try
import shapeless._
import scala.util.Failure
import java.nio.charset.StandardCharsets

import radium226.changes.pgoutput.protocol._


package object parser {

    trait TupleDataParser[T] {

        def parse(tupleData: TupleData): Try[T]

    }

    object TupleDataParser {

        def instance[T](f: TupleData => Try[T]): TupleDataParser[T] = new TupleDataParser[T] {

            override def parse(tupleData: TupleData): Try[T] = f(tupleData)

        }

        def constant[T](c: T) = instance { _ => Try(c) }

    }

    trait TupleDataValueParser[T] {

        def parse(tupleDataValue: Value): Try[T]

    }

    object TupleDataValueParser {

        def instance[T](f: Value => Try[T]): TupleDataValueParser[T] = new TupleDataValueParser[T] {

            override def parse(tupleDataValue: Value): Try[T] = f(tupleDataValue)

        }

        def constant[T](c: T) = instance { _ => Try(c) }

        def summon[T](implicit tupleDataValueParserForT: TupleDataValueParser[T]) = tupleDataValueParserForT

    }

    trait TupleDataParserInstances {

        implicit val tupleDataParserForHNil: TupleDataParser[HNil] = TupleDataParser.constant(HNil)

        implicit def tupleDataParserForHCons[H, T <: HList](implicit 
            tupleDataValueParserForH: TupleDataValueParser[H],
            tupleDataParserForT: TupleDataParser[T]    
        ): TupleDataParser[H :: T] = TupleDataParser.instance { tupleData =>
            for {
                h <- tupleDataValueParserForH.parse(tupleData.values.head)
                t <- tupleDataParserForT.parse(TupleData(tupleData.values.tail))
            } yield h :: t
        }

        implicit def tupleDataParserForHList[T, ReprOfT <: HList](implicit 
            generic: Generic.Aux[T, ReprOfT], 
            tupleDataParserForReprOfT: TupleDataParser[ReprOfT]
        ): TupleDataParser[T] = TupleDataParser.instance { tupleData =>
            tupleDataParserForReprOfT
                .parse(tupleData)
                .map({ reprOfT =>
                    generic.from(reprOfT)
                })
        }

    }

    trait TupleDataValueParserInstances {

        implicit val tupleDataValueParserForDouble: TupleDataValueParser[Double] = TupleDataValueParser.instance {
            case Value.Text(byteVector) =>
                for {
                    string <- TupleDataValueParser
                        .summon[String]
                        .parse(Value.Text(byteVector))
                    double <- Try(java.lang.Double.parseDouble(string))
                } yield double


            case value =>
                Failure(new Exception(s"Unable to convert ${value}"))
        }

        implicit val tupleDataValueParserForString: TupleDataValueParser[String] = TupleDataValueParser.instance {
            case Value.Text(byteVector) =>
                Try(s"${StandardCharsets.UTF_8.decode(byteVector.toByteBuffer).toString}")
                
            case value =>
                Failure(new Exception(s"Unable to convert ${value}"))
        }

        implicit val tupleDataValueParserForLong: TupleDataValueParser[Long] = TupleDataValueParser.instance {
            case Value.Text(byteVector) =>
                for {
                    string <- TupleDataValueParser
                        .summon[String]
                        .parse(Value.Text(byteVector))
                    long <- Try(java.lang.Long.parseLong(string))
                } yield long
                
            case value =>
                Failure(new Exception(s"Unable to convert ${value}")) 
        }

    }

    trait AllInstances extends TupleDataParserInstances 
                          with TupleDataValueParserInstances

    object instances extends AllInstances

  
}
