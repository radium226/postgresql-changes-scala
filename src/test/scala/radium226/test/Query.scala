package radium226.test

import radium226.test.{SQL, Variable}

case class Query(sql: SQL, variables: List[Variable]) {

  def withVariables(variables: Variable*): Query = {
    copy(variables = variables.toList)
  }

}


object Query {

  def apply(sql: SQL): Query = new Query(sql, List.empty[Variable])

}
