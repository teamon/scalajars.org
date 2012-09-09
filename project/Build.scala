import sbt._
import Keys._
import PlayProject._

import sbtscalaxb.Plugin._
import sbtscalaxb.Plugin.ScalaxbKeys._

object ApplicationBuild extends Build {

    val appName         = "scalajars-web"
    val appVersion      = "0.1.0-SNAPSHOT"

    val appDependencies = Seq(
      "net.debasishg" % "redisclient_2.9.2" % "2.6",
      "org.scalaz" % "scalaz-core_2.9.2" % "7.0.0-M3",
      "org.scalaz" % "scalaz-typelevel_2.9.2" % "7.0.0-M3",
      "eu.teamon" %% "play-navigator" % "0.4.0"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(scalaxbSettings: _*).settings(
      organization := "org.scalajars",
      scalacOptions ++= Seq("-Xlint", "-deprecation", "-unchecked","-encoding", "utf8"),
      resolvers += "teamon.eu repo" at "http://repo.teamon.eu",
      resolvers += "scalajars repo" at "http://scalajars.org/respository",

      packageName in scalaxb in Compile := "org.scalajars.lib.maven",
      xsdSource := new File("http://maven.apache.org/xsd/maven-4.0.0.xsd"),
      sourceGenerators in Compile <+= scalaxb in Compile,

      publishTo := Some("scalajars" at "http://scalajars.org/publish/scalajars/51lqunsotbdilac5v94j9m2b3q"),

      templatesImport ++= Seq(
        "org.scalajars.web.nav",
        "org.scalajars.web.controllers._",
        "org.scalajars.core._"
      )
    )

}
