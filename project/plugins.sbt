libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.14.3",
  "io.circe" %% "circe-parser" % "0.14.3",
)

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.6")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "3.0.8")
