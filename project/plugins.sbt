// have to use version 0.8.0 because 0.9.0 updates to jawn 0.11.0 but sbt brings in jawn 0.10.4
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.8.0",
  "io.circe" %% "circe-generic" % "0.8.0",
  "io.circe" %% "circe-parser" % "0.8.0",
)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.3")
