package radium226.changes.example

import cats.effect._

import radium226.changes.pgoutput.{Capture, CaptureConfig}
import radium226.changes.Change
import radium226.changes.pgoutput.reader.instances._


// https://www.postgresql.org/docs/12/protocol-logicalrep-message-formats.html
object Main extends IOApp {

    override def run(args: List[String]): IO[ExitCode] = {
      val config = CaptureConfig(
        user = "postgres",
        password = "postgres",
        database = "postgres",
        host = "localhost",
        port = 5432,
        slot = "my_slot",
        publications = List("my_publication")
      )

      Capture
        .capture[IO, Person](config)
        .collect({
          case Change.Insert(person) =>
            person
        })
        .evalTap({ person =>
          IO(println(person))
        })
        .compile
        .drain
        .as(ExitCode.Success)
    }

}