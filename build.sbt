import java.io.File
import java.nio.file.Files

val catsVersion = "1.6.1"
val catsEffectVersion = "1.4.0"
val circeVersion = "0.11.1"

lazy val commonSettings = Seq(
  name := "cdp4s",
  scalaVersion := "2.12.8",
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
    "-Xfatal-warnings",
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

  wartremoverErrors ++= Warts.allBut(Wart.Any, Wart.ArrayEquals, Wart.Equals, Wart.Nothing),

  dependencyOverrides += scalaOrganization.value % "scala-library" % scalaVersion.value,
  dependencyOverrides += scalaOrganization.value % "scala-reflect" % scalaVersion.value,

  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3" cross CrossVersion.binary),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),

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
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,

      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
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

    libraryDependencies ++= {
      Seq(
        "com.spinoco" %% "fs2-http" % "0.4.1",
        "co.fs2" %% "fs2-core" % "1.0.5",
        "co.fs2" %% "fs2-io" % "1.0.5",

        "org.typelevel" %% "cats-core" % catsVersion,

        "io.circe" %% "circe-core" % circeVersion,
        "io.circe" %% "circe-generic" % circeVersion,
        "io.circe" %% "circe-parser" % circeVersion,
      )
    }

  ))
  .dependsOn(core)

lazy val example = project.in(file("example"))
  .settings(moduleName := "cdp4s-example")
  .settings(commonSettings)
  .settings(skip in publish := true)
  .dependsOn(fs2)
