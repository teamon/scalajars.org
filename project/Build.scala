import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "scalajars"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "eu.teamon" %% "play-navigator" % "0.2.1-SNAPSHOT"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resolvers += "teamon.eu repo" at "http://repo.teamon.eu"
    )

}
