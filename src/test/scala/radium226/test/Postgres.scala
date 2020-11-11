package radium226.test

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cats.effect.{Blocker, IO, Resource}
import com.google.common.io.MoreFiles
import fs2.Stream
import scodec.bits.BitVector

import scala.concurrent.duration._
import cats.implicits._

trait Postgres {
  self: RunIO =>

  def executeQuery(query: Query)(implicit postgresConfig: PostgresConfig): IO[Unit] = {
    val psqlArgs = postgresConfig.connectionArgs ++ query.variables.foldLeft(List.empty[String])({ case (psqlVariableArgs, (name, value)) =>
      psqlVariableArgs :+ "-v" :+ s"${name}='${value}'"
    })

    val psqlCommand = "psql" +: psqlArgs

    for {
      psqlProcess <- IO({
        new ProcessBuilder()
          .inheritIO()
          .redirectInput(ProcessBuilder.Redirect.PIPE)
          .command(psqlCommand: _*)
          .start()
      })
      psqlOutputStream <- IO(psqlProcess.getOutputStream)
      _ <- IO(psqlOutputStream.write(s"${query.sql};\n".getBytes(StandardCharsets.UTF_8)))
      _ <- IO(psqlOutputStream.flush())
      _ <- IO(psqlOutputStream.close())
    } yield ()
  }

  def receiveLogical()(implicit postgresConfig: PostgresConfig): Stream[IO, BitVector] = {
    val processArgs = postgresConfig.connectionArgs ++ List(
      "-d", "postgres",
      "--slot=example_slot",
      "--file=-",
      "--no-loop",
      "--option=proto_version=1",
      "--option=publication_names=example_publication",
      "--plugin=pgoutput",
      "--create-slot",
      "--start"
    )

    val processCommand = ("pg_recvlogical" +: processArgs) ++ postgresConfig.connectionArgs

    for {
      process <- Stream
        .bracket(IO(new ProcessBuilder()
          .command(processCommand: _*)
          .inheritIO()
          .redirectOutput(ProcessBuilder.Redirect.PIPE)
          .start()
        ))({ process =>
          IO(process.destroy())
        })
      blocker <- Stream.resource(Blocker[IO])
      byteChunk <- fs2.io.readInputStream(IO(process.getInputStream), 1, blocker).split(_.toChar == '\n')
    } yield byteChunk.toBitVector
  }

  def postgres(initQueries: List[Query]): Resource[IO, PostgresConfig] = {
    val dataFolderPath = Paths.get("postgres")
    val user = "postgres"
    val port = 5432
    val host = "localhost"

    val postgresArgs = List(
      "-D", s"${dataFolderPath}",
      "-c", s"wal_level=logical",
      "-c", s"port=${port}",
      "-c", s"max_replication_slots=1",
      "-c", s"unix_socket_directories=${Paths.get(System.getProperty("user.dir"))}"
    )

    implicit val postgresConfig = PostgresConfig(
      port,
      host,
      user
    )

    val databaseResource = Resource
      .make(for {
        _ <- IO(Files.createDirectories(dataFolderPath))
        _ <- IO(
          new ProcessBuilder()
            .inheritIO()
            .command(
              "initdb",
              "-D", s"${dataFolderPath}",
              "-A", "trust",
              "-U", user
            )
            .start()
            .waitFor()
        )
      } yield dataFolderPath)({ folderPath =>
        IO(MoreFiles.deleteDirectoryContents(folderPath))
      })

    val postgresCommand = "postgres" +: postgresArgs
    val processResource = Resource
      .make(IO(
        new ProcessBuilder()
          .command(postgresCommand: _*)
          .inheritIO()
          .start()
      ))({ process =>
        IO(process.destroy()) *> IO(process.waitFor()).void
      })

    def waitFor(): IO[Unit] = {
      val processCommand = "pg_isready" +: postgresConfig.connectionArgs
      for {
        exitCode <- IO(
          new ProcessBuilder()
            .command(processCommand: _*)
            .inheritIO()
            .start()
            .waitFor()
        )
        _ <- if (exitCode == 0) IO.unit
        else IO.sleep(500 milliseconds) *> waitFor()
      } yield ()
    }

    for {
      - <- databaseResource
      _ <- processResource
      _ <- Resource.liftF(waitFor())
      _ <- Resource.liftF(initQueries.traverse({ initQuery =>
        executeQuery(initQuery)
      }))
    } yield postgresConfig
  }

}
