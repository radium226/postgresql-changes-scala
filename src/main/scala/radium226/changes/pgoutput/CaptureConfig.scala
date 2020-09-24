package radium226.changes.pgoutput

case class CaptureConfig(
  user: String,
  password: String,
  database: String,
  host: String,
  port: Int,
  slot: String,
  publications: List[String]
)
