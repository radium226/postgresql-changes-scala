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

import radium226.changes.pgoutput.protocol.Message.Relation


package object codec {

    val PostgresEpoch = LocalDateTime.of(2000, 1, 1, 0, 0, 0)
 
    val timestamp: Codec[Instant] = {
        int64.xmap[Instant](
            { long => 
                Instant
                    .from(ZonedDateTime.of(PostgresEpoch, ZoneId.systemDefault()))
                    .plus(Duration.of(long, ChronoUnit.MICROS)) 
            }, 
            { instant =>
                ChronoUnit.MICROS.between(PostgresEpoch, instant)
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
        constant(0) ::
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

    val column: Codec[Column] = {
        ("flags" | int8) ::
        ("name" | cstring) ::
        ("dataTypeID" | int32) ::
        ("typeModifier" | int32)
    }.as[Column]

    val relation: Codec[Relation] = {
        ("id" | int32) ::
        ("namespace" | cstring) ::
        ("name" | cstring) ::
        ("replicaIdentitySetting" | int8) ::
        ("columns" | listOfN(int16, column))
    }.as[Relation]

    val message: Codec[Message] = {
        discriminated[Message].by(byte)
            .typecase('B', begin)
            .typecase('C', commit)
            .typecase('I', insert)
            .typecase('U', update)
            .typecase('R', relation)
    }
    
}