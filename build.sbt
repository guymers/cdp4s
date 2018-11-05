import java.io.File
import java.nio.file.Files

val catsVersion = "1.4.0"
val circeVersion = "0.10.1"

lazy val commonSettings = Seq(
  name := "cdp4s",
  scalaVersion := "2.12.7",
  crossScalaVersions := Seq("2.11.12", scalaVersion.value),

  // https://tpolecat.github.io/2017/04/25/scalac-flags.html
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-explaintypes",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xcheckinit",
//    "-Xfatal-warnings",
    "-Xfuture",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ypartial-unification",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-value-discard"
  ),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) if minor >= 12 => Seq("-Ywarn-extra-implicit")
    case _ => Seq.empty
  }),

  scalacOptions in (Compile, console) --= Seq(
    "-Xfatal-warnings",
    "-Ywarn-unused"
  ),

//  wartremoverErrors := Seq.empty,
//  wartremoverErrors := Warts.allBut(Wart.Nothing),

  dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value,
  dependencyOverrides += "org.scala-lang" % "scala-reflect" % scalaVersion.value,

  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M11" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),

  fork := true,

  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
)


lazy val cdp4s = project.in(file("."))
  .settings(commonSettings)
  .settings(skip in publish := true)
  .aggregate(core, fs2, example)

lazy val protocolJsonFile = (file(".") / "project" / "protocol.json").getAbsoluteFile

lazy val core = project.in(file("core"))
  .settings(moduleName := "cdp4s-core")
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "io.frees" %% "frees-core" % "0.8.2",

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,

      "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    ),

    sourceGenerators in Compile += Def.task[Seq[File]] {
      val dir = (sourceManaged in Compile).value.toPath
      val generated = ProtocolCodeGen.generate(protocolJsonFile)

      val filesToWrite = generated.map { case (path, lines) => (dir.resolve(path), lines) }
      filesToWrite.keySet.map(_.getParent).foreach(p => Files.createDirectories(p))
      filesToWrite.map { case (path, lines) => FileUtils.writeLinesToFile(path, lines).toFile }.toSeq
    }.taskValue

  ))

lazy val fs2 = project.in(file("fs2"))
  .settings(moduleName := "cdp4s-fs2")
  .settings(commonSettings)
  .settings(Seq(
    addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.8" cross CrossVersion.binary),

    libraryDependencies ++= {
      Seq(
        "com.spinoco" %% "fs2-http" % "0.4.0",
        // relying on transitive fs2 dependency from `fs2-http`

        "io.circe" %% "circe-core" % circeVersion,
        "io.circe" %% "circe-generic" % circeVersion,
        "io.circe" %% "circe-parser" % circeVersion,
        "io.circe" %% "circe-scodec" % circeVersion,

        "org.typelevel" %% "cats-core" % catsVersion,
        "org.typelevel" %% "cats-free" % catsVersion,
      )
    }

  ))
  .dependsOn(core)

lazy val example = project.in(file("example"))
  .settings(moduleName := "cdp4s-example")
  .settings(commonSettings)
  .settings(skip in publish := true)
  .dependsOn(fs2)
