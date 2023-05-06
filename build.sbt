// format: off

import java.io.File
import java.nio.file.Files

val catsVersion = "2.9.0"
val catsEffectVersion = "3.4.10"
val circeVersion = "0.14.5"
val fs2Version = "3.6.1"
val scalaTestVersion = "3.2.15"

val Scala213 = "2.13.10"
val Scala3 = "3.2.2"

inThisBuild(Seq(
  organization := "io.github.guymers",
  homepage := Some(url("https://github.com/guymers/cdp4s")),
  licenses := List(License.Apache2),
  developers := List(
    Developer("guymers", "Sam Guymer", "@guymers", url("https://github.com/guymers"))
  ),
  scmInfo := Some(ScmInfo(url("https://github.com/guymers/cdp4s"), "git@github.com:guymers/cdp4s.git")),
))

lazy val commonSettings = Seq(
  scalaVersion := Scala213,
  crossScalaVersions := Seq(Scala213, Scala3),
  versionScheme := Some("pvp"),

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
      "-explain",
      "-explain-types",
      "-no-indent",
      "-source:future",
      "-Wconf:cat=deprecation:silent", // cannot scope to a src
      "-Xmax-inlines", "64",
    )
    case _ => Seq.empty
  }),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
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

  Compile / console / scalacOptions ~= filterScalacConsoleOpts,
  Test / console / scalacOptions ~= filterScalacConsoleOpts,

  Compile / compile / wartremoverErrors := (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Warts.all
    case Some((3, _)) => Seq.empty // ooms
    case _ => Seq.empty
  }),
  Compile / compile / wartremoverErrors --= Seq(
    Wart.Any,
    Wart.Nothing,
  ),
  Test / compile / wartremoverErrors := (Compile / compile / wartremoverErrors).value,
  Test / compile / wartremoverErrors --= Seq(
    Wart.Equals,
    Wart.ImplicitParameter,
  ),

  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
    )
    case Some((3, _)) => Seq.empty
    case _ => Seq.empty
  }),

  fork := true,
  Test / fork := false,
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars,

  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
)

def filterScalacConsoleOpts(options: Seq[String]) = {
  options.filterNot { opt =>
    opt == "-Xfatal-warnings" || opt.startsWith("-Xlint") || opt.startsWith("-W")
  }
}

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
      "org.typelevel" %% "cats-effect-kernel" % catsEffectVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion % Optional,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,

      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
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

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      "co.fs2" %% "fs2-core" % fs2Version,
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
