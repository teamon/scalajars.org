package org.scalajars.core


import scalaz._, Scalaz._

case class User(login: String, email: String, name: String)
case class UserToken(token: String)
object UserToken {
  def random = UserToken(DigestUtils.newRandomToken())
}

case class Project(name: String, description: String, user: String, versions: List[Version])
case class Version(id: String, scalaVersions: List[ScalaVersion]){
  lazy val idSplitted = id.split("-").head.split("\\.").map(e => parseInt(e) | 0).toList
  lazy val isSnapshot = id.contains("SNAPSHOT")
}
case class ScalaVersion(id: String, artifacts: List[Artifact])
case class Artifact(id: String, groupId: String, dependencies: List[Dependency], files: ArtifactFiles){
  lazy val name = groupId + "." + id
}
case class Dependency(groupId: String, artifactId: String, version: String, scope: String){
  lazy val artifactName = artifactId match {
    case ArtifactId(name, scalaVersion) => name
    case _ => artifactId
  }
}

case class ArtifactFiles(pom: Option[String] = None, jar: Option[String] = None, war: Option[String] = None, sources: Option[String] = None, javadoc: Option[String] = None){
  def merge(that: ArtifactFiles) = ArtifactFiles(
    that.pom orElse this.pom,
    that.jar orElse this.jar,
    that.war orElse this.war,
    that.sources orElse this.sources,
    that.javadoc orElse this.javadoc
  )

  lazy val seq = List("POM" -> pom, "JAR" -> jar, "WAR" -> war, "Sources" -> sources, "Javadoc" -> javadoc).filter(_._2.isDefined)
}
object ArtifactFiles {
  def empty = ArtifactFiles(none, none, none, none, none)

  import ArtifactFileType._

  def forPath(path: Path, fileType: ArtifactFileType.FileType) = fileType match {
    case Javadoc  => ArtifactFiles(javadoc = some(path.str))
    case Sources  => ArtifactFiles(sources = some(path.str))
    case Pom      => ArtifactFiles(pom = some(path.str))
    case Jar      => ArtifactFiles(jar = some(path.str))
    case _        => ArtifactFiles.empty
  }
}

object ArtifactId {
  def unapply(name: String): Option[(String, String)] = {
    val idx = name.lastIndexOf('_')
    if(idx != -1){
      val (a,b) = (name.splitAt(idx) :-> (_.drop(1)))
      val idx2 = a.lastIndexOf('_')
      if(idx2 != -1){
        some(a.splitAt(idx2) :-> (_.drop(1)))
      } else {
        some(a,b)
      }
    } else {
      none
    }
  }
}

object ArtifactFileType {
  sealed abstract class FileType(val ending: String)
  case object Javadoc extends FileType("-javadoc.jar")
  case object Sources extends FileType("-sources.jar")
  case object Pom extends FileType(".pom")
  case object Jar extends FileType(".jar")
  case object MD5 extends FileType(".md5")
  case object SHA1 extends FileType(".sha1")

  val All = Javadoc :: Sources :: Pom :: Jar :: MD5 :: SHA1 :: Nil
}

sealed trait IndexItem {
  def name: String
  def withPath(path: Path): IndexItem
  def path: Path
}
case class IndexPackage(name: String, path: Path) extends IndexItem {
  def withPath(p: Path) = copy(path = p)
}
case class IndexFile(name: String, path: Path) extends IndexItem {
  def withPath(p: Path) = copy(path = p)
}


case class Path(parts: IndexedSeq[String]){
  lazy val str = parts.mkString("/")
  lazy val list = parts.toList
  lazy val absolute = "/" + str
  def endsWith(str: String) = parts.lastOption.filter(_.endsWith(str)).isDefined
  def reversed = Path(parts.reverse)
  def base = Path(parts.dropRight(1))
  def baseOption = if(parts.isEmpty) none else some(base)
  def baseAndLast = parts.lastOption.map(l => (base, l))
  def /(s: String) = Path(parts :+ s)
  def /(that: Path) = Path(this.parts ++ that.parts)
}

object Path {
  def apply(str: String): Path = Path(str.split('/').dropWhile(""==).toIndexedSeq)
  val root = Path(Vector.empty[String])
}


case object UnknownException extends Exception
case class CombinedException(t1: Throwable, t2: Throwable) extends Exception

case object WrongPath extends Exception
case object UnsupportedFileType extends Exception
case object InvalidPomFile extends Exception
case object ProjectNotFound extends Exception
case object ArtifactNotFound extends Exception
case object ArtifactFilesNotFound extends Exception
case object IndexNotFound extends Exception
case object TokenNotFound extends Exception
case object UserNotFound extends Exception
case object Unauthorized extends Exception
