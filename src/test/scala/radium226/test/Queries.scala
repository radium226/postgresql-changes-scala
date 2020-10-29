package radium226.test

trait Queries {

  val CreatePersonsTableQuery = Query(
    """CREATE TABLE
      |  persons(
      |    id SERIAL PRIMARY KEY,
      |    first_name TEXT,
      |    last_name TEXT
      |  );
      |
      |ALTER TABLE
      |  persons
      |REPLICA IDENTITY
      |  FULL;""".stripMargin
  )

  val CreateReplicationQuery = Query(
    """CREATE PUBLICATION
      |  example_publication
      |FOR
      |  ALL TABLES;""".stripMargin
  )

  val InitQueries = List(
    CreatePersonsTableQuery,
    CreateReplicationQuery
  )

}
