package org.scalajars

import sbt._
import sbt.Keys._
import util.control.Exception.allCatch

object ScalajarsPlugin extends Plugin {
  private[scalajars] sealed trait ScalajarsToken
  private[scalajars] case class ScalajarsFileToken(file: File) extends ScalajarsToken
  private[scalajars] case class ScalajarsStringToken(string: String) extends ScalajarsToken
  object ScalajarsToken {
    def apply(string: String) = ScalajarsStringToken(string)
    def apply(file: File) = ScalajarsFileToken(file)
    def read(token: ScalajarsToken): Either[Throwable, String] = token match {
      case ScalajarsStringToken(string) => Right(string)
      case ScalajarsFileToken(file)     => allCatch either IO.read(file).trim
    }
  }

  val scalajarsProjectName = SettingKey[String]("scalajars-project-name", "scalajars.org project name")
  val scalajarsToken = SettingKey[ScalajarsToken]("scalajars-token", "scalajars.org token")

  val scalajarsSettings = Seq(
    scalajarsToken := ScalajarsToken(Path.userHome / ".scalajars"),
    publishTo <<= (scalajarsProjectName, scalajarsToken) { (name, token) =>
      ScalajarsToken.read(token) match {
        case Left(ex) =>
          println(scala.Console.YELLOW + "[warn] sbt-scalajars: " + ex.getMessage + scala.Console.RESET)
          None
        case Right(t) =>
          Some("scalajars.org publishing" at ("http://scalajars.org/publish/" + name + "/" + t))
      }
    }
  )
}
