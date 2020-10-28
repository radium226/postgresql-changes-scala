ThisBuild / scalaVersion     := "2.13.3"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "radium226"
ThisBuild / organizationName := "Radium226"
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-language:_",
  "-unchecked",
  "-Wvalue-discard",
  "-Xfatal-warnings",
  "-Ymacro-annotations"
)

lazy val fs2Dependency = for {
  fs2  <- Dependencies.fs2
  cats <- Dependencies.cats
} yield fs2 exclude(cats.organization, cats.name)

lazy val root = (project in file("."))
  .settings(
    addCompilerPlugin(Dependencies.contextApplied),
    name := "changes",
    // cats
    libraryDependencies ++= Dependencies.cats,
    // fs2
    libraryDependencies ++= (for {
      fs2  <- Dependencies.fs2
      cats <- Dependencies.cats
    } yield fs2 exclude(cats.organization, cats.name)),
    // scala-test
    libraryDependencies ++= Dependencies.scalaTest map { _ % Test },
    libraryDependencies += "com.codecommit" %% "cats-effect-testing-scalatest" % "0.4.1" % Test,
    libraryDependencies += "com.github.javafaker" % "javafaker" % "1.0.2" % Test,
    libraryDependencies += "com.google.guava" % "guava" % "30.0-jre" % Test,

      // slf4j
    libraryDependencies ++= Dependencies.slf4j,
    // scodec
    libraryDependencies ++= Dependencies.scodec,
    Compile / mainClass := Some("radium226.changes.example.Main"),
  )