import sbt._
import Keys._
import PlayProject._

import sbtscalaxb.Plugin._
import sbtscalaxb.Plugin.ScalaxbKeys._

object ApplicationBuild extends Build {

    val appName         = "scalajars"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "net.debasishg" % "redisclient_2.9.2" % "2.6",
      "org.scalaz" % "scalaz-core_2.9.2" % "7.0.0-M3",
      "org.scalaz" % "scalaz-typelevel_2.9.2" % "7.0.0-M3",
      "eu.teamon" %% "play-navigator" % "0.3.1"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(scalaxbSettings: _*).settings(
      scalacOptions ++= Seq("-Xlint", "-deprecation", "-unchecked","-encoding", "utf8"),
      resolvers += "teamon.eu repo" at "http://repo.teamon.eu",

      packageName in scalaxb in Compile := "org.scalajars.lib.maven",
      xsdSource := new File("http://maven.apache.org/xsd/maven-4.0.0.xsd"),
      sourceGenerators in Compile <+= scalaxb in Compile,
      // generateRuntime := false,


      templatesImport ++= Seq(
        "org.scalajars.web.nav",
        "org.scalajars.web.controllers._"
      )
    )

}