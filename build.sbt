import sbt.Resolver

import Common._
import app.softnetwork.sbt.build._

/////////////////////////////////
// Defaults
/////////////////////////////////

app.softnetwork.sbt.build.Publication.settings

/////////////////////////////////
// Useful aliases
/////////////////////////////////

addCommandAlias("cd", "project") // navigate the projects

addCommandAlias("cc", ";clean;compile") // clean and compile

addCommandAlias("pl", ";clean;publishLocal") // clean and publish locally

addCommandAlias("pr", ";clean;publish") // clean and publish globally

addCommandAlias("pld", ";clean;local:publishLocal;dockerComposeUp") // clean and publish/launch the docker environment

addCommandAlias("dct", ";dockerComposeTest") // navigate the projects

shellPrompt in ThisBuild := prompt

organization in ThisBuild := "app.softnetwork"

name := "generic-persistence-api"

version in ThisBuild := "0.1.5"

scalaVersion in ThisBuild := "2.12.11"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature")

resolvers in ThisBuild ++= Seq(
  "Softnetwork Server" at "https://softnetwork.jfrog.io/artifactory/releases/",
  "Maven Central Server" at "https://repo1.maven.org/maven2",
  "Typesafe Server" at "https://repo.typesafe.com/typesafe/releases"
)

libraryDependencies in ThisBuild ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
)

parallelExecution in Test := false

val pbSettings = Seq(
  PB.targets in Compile := Seq(
    scalapb.gen() -> crossTarget.value / "protobuf_managed/main"
  )
)

lazy val common = project.in(file("common"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)

lazy val commonTestkit = project.in(file("common/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val core = project.in(file("core"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val coreTestkit = project.in(file("core/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    commonTestkit % "compile->compile;test->test;it->it"
  )

lazy val jdbc = project.in(file("jdbc"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val jdbcTestkit = project.in(file("jdbc/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    jdbc % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )

lazy val counter = project.in(file("counter"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val scheduler = project.in(file("scheduler"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val session = project.in(file("session"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val notification = project.in(file("notification"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )

lazy val elasticTestkit = project.in(file("elastic/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    commonTestkit % "compile->compile;test->test;it->it"
  )

lazy val elastic = project.in(file("elastic"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )
  .dependsOn(
    elasticTestkit % "test->test;it->it"
  )

lazy val server = project.in(file("server"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val serverTestkit = project.in(file("server/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )

lazy val root = project.in(file("."))
  .aggregate(
    common,
    commonTestkit,
    core,
    coreTestkit,
    jdbc,
    jdbcTestkit,
    counter,
    scheduler,
    session,
    notification,
    elasticTestkit,
    elastic,
    server,
    serverTestkit
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)

envVars in Test := Map(
  "POSTGRES_USER" -> "admin",
  "POSTGRES_PASSWORD" -> "changeit",
  "POSTGRES_5432" -> "15432"
)
