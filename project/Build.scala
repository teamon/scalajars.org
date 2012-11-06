import sbt._
import Keys._
import PlayProject._

import sbtscalaxb.Plugin._
import sbtscalaxb.Plugin.ScalaxbKeys._
import org.scalajars.ScalajarsPlugin._

object ApplicationBuild extends Build {
  val commonSettings = scalajarsSettings ++ seq(
    organization := "org.scalajars",
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-unchecked","-encoding", "utf8"),
    resolvers += "teamon.eu repo" at "http://repo.teamon.eu",
    resolvers += "scalajars repo" at "http://scalajars.org/repository"
  )

  val appName         = "scalajars-web"
  val appVersion      = "0.1.0-SNAPSHOT"

  val appDependencies = Seq(
    "net.debasishg" % "redisclient_2.9.2" % "2.6",
    "org.scalaz" % "scalaz-core_2.9.2" % "7.0.0-M3",
    "org.scalaz" % "scalaz-typelevel_2.9.2" % "7.0.0-M3",
    "eu.teamon" %% "play-scalaz" % "0.1.1-SNAPSHOT",
    "eu.teamon" %% "play-navigator" % "0.4.0"
  )

  lazy val root = Project("scalajars", file(".")).aggregate(webapp, plugin)

  lazy val webapp = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA)
    .settings((scalaxbSettings ++ commonSettings): _*).settings(
    packageName in scalaxb in Compile := "org.scalajars.lib.maven",
    xsdSource := new File("http://maven.apache.org/xsd/maven-4.0.0.xsd"),
    sourceGenerators in Compile <+= scalaxb in Compile,

    scalajarsProjectName := "scalajars",

    templatesImport ++= Seq(
      "org.scalajars.web.nav",
      "org.scalajars.web.controllers._",
      "org.scalajars.core._"
    )
  )

  lazy val plugin = Project("sbt-scalajars", file("sbt-scalajars")).settings(commonSettings:_*).settings(
    version := "0.1.1",
    sbtPlugin := true,
    sbtVersion := "0.11.3",
    scalajarsProjectName := "scalajars"
  )

}
