// format: off
import java.io.File
import java.nio.file.Files

val catsVersion = "2.8.0"
val catsEffectVersion = "3.3.14"
val circeVersion = "0.14.3"
val fs2Version = "3.3.0"
val zioVersion = "2.0.5"
val zioCatsVersion = "23.0.0.0"

val Scala212 = "2.12.17"
val Scala213 = "2.13.10"
val Scala3 = "3.1.3"

inThisBuild(Seq(
  organization := "io.github.guymers",
  homepage := Some(url("https://github.com/guymers/cdp4s")),
  licenses := List(License.Apache2),
  developers := List(
    Developer("guymers", "Sam Guymer", "@guymers", url("https://github.com/guymers"))
  ),
  scmInfo := Some(ScmInfo(url("https://github.com/guymers/cdp4s"), "git@github.com:guymers/cdp4s.git")),
))

val IntegrationTest_ = sbt.config("it") extend Test
val IntegrationTest = IntegrationTest_

Global / excludeLintKeys ++= Set(IntegrationTest_ / fork, IntegrationTest_ / javaOptions)

lazy val commonSettings = Seq(
  scalaVersion := Scala213,
  crossScalaVersions := Seq(Scala212, Scala213, Scala3),
  versionScheme := Some("early-semver"),

  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
  ),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(
      "-explaintypes",
      "-language:existentials",
      "-language:higherKinds",
      "-Wconf:src=src_managed/.*&cat=deprecation:silent",
      "-Xsource:3",
    )
    case Some((3, _)) => Seq(
      "-explain-types",
      "-source:future",
    )
    case _ => Seq.empty
  }),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq(
      "-Xfuture",
      "-Yno-adapted-args",
      "-Ypartial-unification",
    )
    case Some((2, minor)) if minor >= 13 => Seq(
      "-Vimplicits",
      "-Vtype-diffs",
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnonunit-statement",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wunused:_",
      "-Wperformance",
      "-Wvalue-discard",

      "-Xlint:_,-byname-implicit", // exclude byname-implicit https://github.com/scala/bug/issues/12072
    )
    case _ => Seq.empty
  }),

  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
    case _ => Seq.empty
  }),

  Compile / console / scalacOptions ~= filterScalacConsoleOpts,
  Test / console / scalacOptions ~= filterScalacConsoleOpts,

  Compile / compile / wartremoverErrors := Warts.all,
  Compile / compile / wartremoverErrors --= Seq(
    Wart.Any,
    Wart.ArrayEquals,
    Wart.DefaultArguments,
    Wart.Equals,
    Wart.Nothing,
    Wart.ToString,
  ),
  Test / compile / wartremoverErrors := Seq(
    Wart.NonUnitStatements,
    Wart.Null,
    Wart.Return,
  ),

  fork := true,
  Test / fork := false,
  IntegrationTest / fork := true,
  IntegrationTest / javaOptions += "-Xmx1000m",
)

def filterScalacConsoleOpts(options: Seq[String]) = {
  options.filterNot { opt =>
    opt == "-Xfatal-warnings" || opt.startsWith("-Xlint") || opt.startsWith("-W")
  }
}

lazy val zioTestSettings = Seq(
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio-test" % zioVersion % Test,
    "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  ),
)

lazy val cdp4s = project.in(file("."))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .aggregate(core, fs2, tests, example)

lazy val protocolJsonFile = (file(".") / "project" / "protocol.json").getAbsoluteFile

lazy val core = project.in(file("core"))
  .settings(moduleName := "cdp4s-core")
  .settings(commonSettings)
  .settings(zioTestSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect-kernel" % catsEffectVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
    ),

    Compile / sourceGenerators += Def.task[Seq[File]] {
      val scala3 = CrossVersion.partialVersion(scalaVersion.value).exists { case (v, _) => v == 3 }
      val dir = (Compile / sourceManaged).value.toPath
      val generated = ProtocolCodeGen.generate(protocolJsonFile, scala3)

      val filesToWrite = generated.map { case (path, lines) => (dir.resolve(path), lines) }
      filesToWrite.keySet.map(_.getParent).foreach(p => Files.createDirectories(p))
      filesToWrite.map { case (path, lines) => FileUtils.writeLinesToFile(path, lines).toFile }.toSeq
    }.taskValue
  ))

lazy val fs2 = project.in(file("fs2"))
  .settings(moduleName := "cdp4s-fs2")
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect-std" % catsEffectVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion % Optional,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version,

      "dev.zio" %% "zio" % zioVersion % Optional,
    ),
  ))
  .dependsOn(core)

lazy val tests = project.in(file("tests"))
  .settings(moduleName := "cdp4s-tests")
  .settings(commonSettings)
  .settings(zioTestSettings)
  .settings(publish / skip := true)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-interop-cats" % zioCatsVersion,
    ),
  ))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(fs2)

lazy val example = project.in(file("example"))
  .settings(moduleName := "cdp4s-example")
  .settings(commonSettings)
  .settings(publish / skip := true)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % zioVersion,
    ),
  ))
  .dependsOn(tests)
