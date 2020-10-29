package radium226.test

import radium226.changes.pgoutput.CaptureConfig

case class PostgresConfig(port: Int, host: String, user: String) {

  def connectionArgs: List[String] = List(
    "-U", user,
    "-p", s"${port}",
    "-h", s"${host}"
  )

  def toCaptureConfig(slot: String, publication: String, database: String = user, password: String = ""): CaptureConfig = CaptureConfig(
    user,
    "",
    database,
    host,
    port,
    slot,
    publications = List(publication)
  )

}
