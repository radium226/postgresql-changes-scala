package radium226.test

import org.scalatest.Assertions
import radium226.changes.Change


trait Changes {
  self: Assertions =>

  implicit class ChangeOps[A](change: Change[A]) {

    def insert: Change.Insert[A] = change match {
      case insert @ Change.Insert(value) =>
        insert

      case _ =>
        fail()
    }

  }

}
