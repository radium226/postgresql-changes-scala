package radium226.changes.pgoutput

//<<< capture-config-class
case class CaptureConfig(
  user: String,
  password: String,
  database: String,
  host: String,
  port: Int,
  slot: String,
  publications: List[String]
)
//>>>
