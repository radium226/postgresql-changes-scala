package radium226

import java.util.{Random => JavaRandom}

import cats.effect.IO
import com.github.javafaker.Faker
import radium226.test.{AbstractSpec, Postgres, PostgresConfig, Queries, Query}

import scala.concurrent.duration._

class LogicalReplicationSpec extends AbstractSpec with Postgres with Queries {

  implicit val faker: Faker = Faker.instance(new JavaRandom(42L))

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
