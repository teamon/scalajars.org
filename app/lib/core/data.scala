package org.scalajars.core

import scalaz._, Scalaz._

case class Project(name: String, description: String, artifacts: List[Artifact])
case class Artifact(id: String, groupId: String, versions: List[Version])
case class Version(id: String, dependencies: List[Dependency])
case class Dependency(groupId: String, artifactId: String, version: String)

object ArtifactFileType {
  sealed abstract class FileType(val ending: String)
  case object Javadoc extends FileType("-javadoc.jar")
  case object Sources extends FileType("-source.jar")
  case object Pom extends FileType(".pom")
  case object Jar extends FileType(".jar")
  case object MD5 extends FileType(".md5")
  case object SHA1 extends FileType(".sha1")

  val All = Javadoc :: Sources :: Pom :: Jar :: MD5 :: SHA1 :: Nil
}

case object UnknownException extends Exception
case class CombinedException(t1: Throwable, t2: Throwable) extends Exception

case object WrongPath extends Exception
case object UnsupportedFileType extends Exception
case object InvalidPomFile extends Exception
case object ProjectNotFound extends Exception
