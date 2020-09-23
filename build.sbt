import java.io.File
import java.nio.file.Files

val catsVersion = "2.2.0"
val catsEffectVersion = "2.2.0"
val circeVersion = "0.13.0"
val fs2Version = "2.4.4"
val sttpVersion = "3.0.0-RC4"
val scalaTestVersion = "3.2.2"

lazy val IntegrationTest = config("it") extend Test

val warnUnused = Seq(
  "explicits",
  "implicits",
  "imports",
  "locals",
  "params",
  "patvars",
  "privates",
)

def filterScalacConsoleOpts(options: Seq[String]) = {
  options.filterNot { opt =>
    opt == "-Xfatal-warnings" ||
    opt.startsWith("-Ywarn-") ||
    opt.startsWith("-W")
  }
}

val Scala212 = "2.12.12"
val Scala213 = "2.13.3"

lazy val commonSettings = Seq(
  name := "cdp4s",
  scalaVersion := Scala212,
  crossScalaVersions := Seq(Scala212, Scala213),
  licenses ++= Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),

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
    //"-Xfatal-warnings",
  ),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) if minor <= 12 => Seq(
      "-Xfuture",
      "-Xlint:_",
      "-Yno-adapted-args",
      "-Ypartial-unification",

      "-Ywarn-dead-code",
      "-Ywarn-extra-implicit",
      "-Ywarn-infer-any",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    ) ++ warnUnused.map(o => s"-Ywarn-unused:$o")
    case _ => Seq(
      "-Xlint:_,-byname-implicit", // exclude byname-implicit https://github.com/scala/bug/issues/12072
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wvalue-discard",
    ) ++ warnUnused.map(o => s"-Wunused:$o")
  }),
  scalacOptions in (Compile, console) ~= filterScalacConsoleOpts,
  scalacOptions in (Test, console) ~= filterScalacConsoleOpts,

  wartremoverErrors := Seq.empty,
  wartremoverErrors in (Compile, compile) ++= Warts.allBut(Wart.Any, Wart.ArrayEquals, Wart.Equals, Wart.Nothing),

  dependencyOverrides += scalaOrganization.value % "scala-library" % scalaVersion.value,
  dependencyOverrides += scalaOrganization.value % "scala-reflect" % scalaVersion.value,

  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),

  addCompilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.1" cross CrossVersion.full),
  libraryDependencies += "com.github.ghik" % "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full,
  scalacOptions += "-P:silencer:pathFilters=src_managed",

  fork := true,

  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
)


lazy val cdp4s = project.in(file("."))
  .settings(commonSettings)
  .settings(skip in publish := true)
  .aggregate(core, sttp, tests, example)

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

      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
    ),

    sourceGenerators in Compile += Def.task[Seq[File]] {
      val dir = (sourceManaged in Compile).value.toPath
      val generated = ProtocolCodeGen.generate(protocolJsonFile)

      val filesToWrite = generated.map { case (path, lines) => (dir.resolve(path), lines) }
      filesToWrite.keySet.map(_.getParent).foreach(p => Files.createDirectories(p))
      filesToWrite.map { case (path, lines) => FileUtils.writeLinesToFile(path, lines).toFile }.toSeq
    }.taskValue
  ))

lazy val sttp = project.in(file("sttp"))
  .settings(moduleName := "cdp4s-sttp")
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      "co.fs2" %% "fs2-io" % fs2Version,
      "com.softwaremill.sttp.client3" %% "fs2" % sttpVersion,
    ),
  ))
  .dependsOn(core)

lazy val tests = project.in(file("tests"))
  .settings(moduleName := "cdp4s-tests")
  .settings(commonSettings)
  .settings(skip in publish := true)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % sttpVersion,

      "org.scalatest" %% "scalatest" % scalaTestVersion,
    ),
  ))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(sttp)

lazy val example = project.in(file("example"))
  .settings(moduleName := "cdp4s-example")
  .settings(commonSettings)
  .settings(skip in publish := true)
  .dependsOn(tests)
