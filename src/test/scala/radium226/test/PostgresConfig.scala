package radium226.test

case class PostgresConfig(port: Int, host: String, user: String) {

  def connectionArgs: List[String] = List(
    "-U", user,
    "-p", s"${port}",
    "-h", s"${host}"
  )

}
