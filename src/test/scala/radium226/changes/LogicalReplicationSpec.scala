package radium226.changes

import cats.effect.IO
import com.github.javafaker.Faker
import radium226.test.Query
import java.util.{Random => JavaRandom}

import radium226.test.{AbstractSpec, Postgres, PostgresConfig}

import scala.concurrent.duration._

class LogicalReplicationSpec extends AbstractSpec with Postgres {

  implicit val faker: Faker = Faker.instance(new JavaRandom(42L))

  val CreatePersonsTableQuery = Query(
    """CREATE TABLE
      |  persons(
      |    id SERIAL PRIMARY KEY,
      |    first_name TEXT,
      |    last_name TEXT
      |  );""".stripMargin
  )

  val CreateReplicationQuery = Query(
    """CREATE PUBLICATION
      |  example_publication
      |FOR
      |  ALL TABLES;""".stripMargin
  )

  val InsertIntoPersonsQuery = Query(
    """INSERT INTO
      |  persons (
      |    first_name,
      |    last_name
      |  )
      |VALUES (
      |  :first_name,
      |  :last_name
      |);""".stripMargin
  )

  def insertPersons(implicit faker: Faker, postgresConfig: PostgresConfig): IO[Unit] = {
    for {
      _ <- executeQuery(InsertIntoPersonsQuery.withVariables(
        "first_name" -> faker.name().firstName(),
        "last_name" -> faker.name().lastName()
      ))
      _ <- IO.sleep(1 second)
      _ <- insertPersons
    } yield ()
  }

  val InitQueries = List(
    CreatePersonsTableQuery,
    CreateReplicationQuery
  )

  "Logical replication" should "emit binary stream when changes occur in Postgres" in runIO(postgres(InitQueries).use({ implicit postgresConfig =>
    duringIO(insertPersons) {
      receiveLogical()
        .interruptAfter(5 seconds)
        .compile
        .toList
        .map(_.size should be > 0)
    }
  }))

}
