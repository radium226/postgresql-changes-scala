package radium226.changes


sealed trait Change[T]

object Change {

    case class Insert[T](newValue: T) extends Change[T]

    case class Update[T](oldValue: T, newValue: T) extends Change[T]

    case class Delete[T](oldValue: T) extends Change[T]

}