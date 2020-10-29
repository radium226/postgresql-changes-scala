package radium226.changes.pgoutput

import cats.effect.{ExitCase, IO}
import cats.implicits._
import com.github.javafaker.Faker
import org.scalatest.Inspectors
import radium226.changes.Change
import radium226.changes.pgoutput.reader.TupleDataReader
import radium226.test._
import java.util.{Random => JavaRandom}

import scala.concurrent.duration._
import radium226.changes.pgoutput.reader.instances._


class CaptureSpec extends AbstractSpec with Postgres with Queries with Inspectors with Changes {

  implicit val faker: Faker = Faker.instance(new JavaRandom(42L))

  val firstSQL =
    """BEGIN;
      |
      |INSERT INTO
      |  persons(
      |    first_name,
      |    last_name
      |  )
      |VALUES (
      |  'Albert',
      |  'Einstein'
      |);
      |
      |INSERT INTO
      |  persons(
      |    first_name,
      |    last_name
      |  )
      |VALUES (
      |  'Niels',
      |  'Bohr'
      |);
      |
      |COMMIT;""".stripMargin

  val secondSQL =
    """BEGIN;
      |
      |UPDATE
      |  persons
      |SET
      |  first_name = 'Isaac',
      |  last_name = 'Newton'
      |WHERE
      |  first_name = 'Albert';
      |
      |DELETE FROM
      |  persons
      |WHERE
      |  first_name = 'Niels';
      |
      |COMMIT;""".stripMargin

  val thirdSQL =
    """BEGIN;
      |
      |DELETE FROM
      |  persons;
      |
      |COMMIT;""".stripMargin

  def simulateWorkload(implicit postgresConfig: PostgresConfig): IO[Unit] = {
    for {
      _ <- List(firstSQL, secondSQL, thirdSQL).traverse_({ sql =>
        executeQuery(Query(sql)) *> IO.sleep(1 second)
      })
      _ <- simulateWorkload
    } yield ()
  }

  "Capture" should "be able to capture changes" in runIO(postgres(InitQueries).use({ implicit postgresConfig =>

    case class Person(firstName: String, lastName: String)

    duringIO(simulateWorkload) {
      Capture
        .captureChanges[IO, Person](CaptureConfig(
          host = "localhost",
          port = 5432,
          user = "postgres",
          password = "",
          database = "postgres",
          publications = List("example_publication"),
          slot = "example_slot"
        ))
        .interruptAfter(10 seconds)
        .compile
        .toList
        .map({ changes =>
          changes.size should be > 0

          forAll(changes) {
            case Change.Insert(newPerson) =>
              println(s"Change.Insert(${newPerson})")
              newPerson shouldBe a[Person]

            case Change.Update(oldPerson, newPerson) =>
              println(s"Change.Update(${oldPerson}, ${newPerson})")
              oldPerson shouldBe a[Person]
              newPerson shouldBe a[Person]

            case Change.Delete(oldPerson) =>
              println(s"Change.Delete(${oldPerson})")
              oldPerson shouldBe a[Person]
          }
        })
    }
  }), 20 seconds)

  val CreatePersonAndJobTablesQueries = List(
    Query(
      """CREATE TABLE
        |  persons(
        |     id SERIAL PRIMARY KEY,
        |     first_name TEXT,
        |     last_name TEXT,
        |     job_id SERIAL
        |  );
        |
        |ALTER TABLE
        |  persons
        |REPLICA IDENTITY
        |  FULL;""".stripMargin
    ),

    Query(
      """CREATE TABLE
        |  jobs(
        |     id SERIAL PRIMARY KEY,
        |     title TEXT
        |  );
        |
        |ALTER TABLE
        |  persons
        |REPLICA IDENTITY
        |  FULL;""".stripMargin
    ),

    CreateReplicationQuery
  )

  val InsertJobAndPersonQuery = Query(
    """WITH result as (
      |  INSERT INTO
      |    jobs(
      |      title
      |    )
      |  VALUES (
      |    :title
      |  )
      |  RETURNING
      |    id AS job_id
      |)
      |INSERT INTO
      |  persons (
      |    first_name,
      |    last_name,
      |    job_id
      |  )
      |SELECT
      |  :first_name AS first_name,
      |  :last_name AS last_name,
      |  r.job_id
      |FROM
      |  result AS r;""".stripMargin)


  def insertJobAndPerson(implicit postgresConfig: PostgresConfig): IO[Unit] = {
    val firstName = faker.name().firstName()
    val lastName = faker.name().lastName()
    val title = faker.job().title()
    for {
      _ <- executeQuery(InsertJobAndPersonQuery.withVariables(
        "first_name" -> firstName,
        "last_name" -> lastName,
        "title" -> title
      ))
      - <- IO.sleep(1 second)
      _ <- insertJobAndPerson
    } yield ()
  }

  "Capture" should "be able to capture transactions" in runIO(postgres(CreatePersonAndJobTablesQueries).use({ implicit postgresConfig =>

    case class Person(id: Long, firstName: String, lastName: String, jobId: Long)

    case class Job(id: Long, title: String)

    duringIO(insertJobAndPerson) {
      Capture.captureTransactions[IO](
        postgresConfig.toCaptureConfig(
          slot = "example_slot",
          publication = "example_publication"
        ),
        Map(
          "persons" -> TupleDataReader[Person],
          "jobs" -> TupleDataReader[Job]
        )
      )
        .interruptAfter(10 seconds)
        .compile
        .toList
        .map({ transactions =>
          forAll(transactions) { transactions =>
            val changes = transactions.changes
            all(changes) shouldBe a[Change.Insert[_]]

            val newValues = changes.map(_.insert.newValue)

            info(s"newValues=${newValues}")
            exactly(1, newValues) shouldBe a[Job]
            exactly(1, newValues) shouldBe a[Person]
          }
        })
    }
  }), 20 seconds)

}
