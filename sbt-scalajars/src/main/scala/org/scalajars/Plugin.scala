package org.scalajars

import sbt._
import sbt.Keys._

object ScalajarsPlugin extends Plugin {
  private[scalajars] sealed trait ScalajarsToken
  private[scalajars] case class ScalajarsFileToken(file: File) extends ScalajarsToken
  private[scalajars] case class ScalajarsStringToken(string: String) extends ScalajarsToken
  object ScalajarsToken {
    def apply(string: String) = ScalajarsStringToken(string)
    def apply(file: File) = ScalajarsFileToken(file)
    def read(token: ScalajarsToken): String = token match {
      case ScalajarsStringToken(string) => string
      case ScalajarsFileToken(file)     => IO.read(file).trim
    }
  }

  val scalajarsProjectName = SettingKey[String]("scalajars-project-name", "scalajars.org project name")
  val scalajarsToken = SettingKey[ScalajarsToken]("scalajars-token", "scalajars.org token")

  val scalajarsSettings = Seq(
    scalajarsToken := ScalajarsToken(Path.userHome / ".scalajars"),
    publishTo <<= (scalajarsProjectName, scalajarsToken) { (name, token) =>
      Some("scalajars.org publishing" at ("http://scalajars.org/publish/" + name + "/" + ScalajarsToken.read(token)))
    }
  )
}
