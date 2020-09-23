package radium226.changes.pgoutput
package protocol

import scodec._
import scodec.bits._
import scodec.codecs._
import scodec.codecs.implicits._

import cats._
import cats.implicits._

import java.time.LocalDateTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId


package object codec {
 
    val timestamp: Codec[Instant] = {
        int64.xmap[Instant](
            { long => 
                Instant
                    .from(ZonedDateTime.of(LocalDateTime.of(2000, 1, 1, 0, 0, 0), ZoneId.systemDefault()))
                    .plus(Duration.of(long, ChronoUnit.MICROS)) 
            }, 
            { instant =>
                ChronoUnit.MICROS.between(Instant.EPOCH, instant)
            }
        )
    }

    val value: Codec[Value] = {
        discriminated[Value].by(byte)
            .typecase('n', provide(Value.Null))
            .typecase('u', provide(Value.Toasted))
            .typecase('t', int32.consume({ length => bytes(length) })(_.size.toInt).as[Value.Text])
    }

    val tupleData: Codec[TupleData] = listOfN(int(16), value).hlist.as[TupleData]

    val submessage: Codec[Submessage] = {
        discriminatorFallback(
            provide(Submessage.Nothing), 
            discriminated[Submessage].by(peek(byte))
                .typecase('K', (constant('K') :: ("keyTupleData" | tupleData)).as[Submessage.Key])
                .typecase('O', (constant('O') :: ("oldTupleData" | tupleData)).as[Submessage.Old])
        )
        .xmap(_.fold(identity, identity), {
            case Submessage.Nothing =>
                Left(Submessage.Nothing)

            case submessage =>
                Right(submessage)
        })
    }

    val begin: Codec[Message.Begin] = {
        ("lsn" | int64) ::
        ("commitInstant" | timestamp) :: 
        ("xid" | int32)  
    }.as[Message.Begin]

    val commit: Codec[Message.Commit] = {
        ("flags" | int8) :: 
        ("commitLSN" | int64) :: 
        ("endTransactionLSN" | int64) :: 
        ("commitTimestamp" | timestamp)
    }.as[Message.Commit]

    val update: Codec[Message.Update] = {
        ("relationID" | int32) :: 
        ("submessage" | submessage) :: 
        constant('N') ::
        ("newTupleData" | tupleData)
    }.as[Message.Update]

    val insert: Codec[Message.Insert] = {
        ("relationId" | int32) ::
        constant('N') ::
        ("newTupleData" | tupleData)
    }.as[Message.Insert]

    val message: Codec[Message] = {
        discriminated[Message].by(byte)
            .typecase('B', begin)
            .typecase('C', commit)
            .typecase('I', insert)
            .typecase('U', update)
    }
    
}