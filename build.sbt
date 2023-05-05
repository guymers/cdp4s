import java.io.File
import java.nio.file.Files

val catsVersion = "2.9.0"
val catsEffectVersion = "3.4.10"
val circeVersion = "0.14.5"
val fs2Version = "3.6.1"
val sttpVersion = "3.8.15"
val scalaTestVersion = "3.2.15"

inThisBuild(Seq(
  organization := "io.github.guymers",
  homepage := Some(url("https://github.com/guymers/cdp4s")),
  licenses := List(License.Apache2),
  developers := List(
    Developer("guymers", "Sam Guymer", "@guymers", url("https://github.com/guymers"))
  ),
  scmInfo := Some(ScmInfo(url("https://github.com/guymers/cdp4s"), "git@github.com:guymers/cdp4s.git")),
))

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

val Scala212 = "2.12.17"
val Scala213 = "2.13.10"

lazy val commonSettings = Seq(
  name := "cdp4s",
  scalaVersion := Scala212,
  crossScalaVersions := Seq(Scala212, Scala213),

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

  Compile / console / scalacOptions ~= filterScalacConsoleOpts,
  Test / console / scalacOptions ~= filterScalacConsoleOpts,

  Compile / compile / wartremoverErrors := Warts.all,
  Compile / compile / wartremoverErrors --= Seq(
    Wart.Any,
    Wart.ArrayEquals,
    Wart.Equals,
    Wart.FinalCaseClass,
    Wart.ImplicitParameter,
    Wart.Nothing,
  ),
  Test / compile / wartremoverErrors := Seq(
    Wart.NonUnitStatements,
    Wart.Null,
    Wart.Return,
  ),

  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),

  addCompilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.12" cross CrossVersion.full),
  libraryDependencies += "com.github.ghik" % "silencer-lib" % "1.7.12" % Provided cross CrossVersion.full,
  scalacOptions += "-P:silencer:pathFilters=src_managed",

  fork := true,
  Test / fork := false,
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars,

  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
)

lazy val cdp4s = project.in(file("."))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .settings(
    addCommandAlias("testUnit", ";modules/test"),
    addCommandAlias("testIntegration", ";integrationTests/test"),
  )
  .aggregate(modules, integrationTests, example)

lazy val modules = project.in(file("project/.root"))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .aggregate(core, fs2)

lazy val integrationTests = project.in(file("project/.root-integration"))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .aggregate(tests)

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

    Compile / sourceGenerators += Def.task[Seq[File]] {
      val dir = (Compile / sourceManaged).value.toPath
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
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      "co.fs2" %% "fs2-io" % fs2Version,
    ),
  ))
  .dependsOn(core)

lazy val tests = project.in(file("tests"))
  .settings(moduleName := "cdp4s-tests")
  .settings(commonSettings)
  .settings(publish / skip := true)
  .settings(Seq(
    Test / fork := true,
    Test / javaOptions += "-Xmx1000m",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion,
    ),
  ))
  .dependsOn(fs2)

lazy val example = project.in(file("example"))
  .settings(moduleName := "cdp4s-example")
  .settings(commonSettings)
  .settings(publish / skip := true)
  .dependsOn(tests)
