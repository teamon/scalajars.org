package org.scalajars.web.controllers

import org.scalajars.web.lib._

import play.api._
import play.api.mvc._
import play.api.libs.Files.TemporaryFile
import play.api.Play.current

import java.io.File

import scalaz._
import Scalaz._


import org.scalajars.lib.maven.Model

import scala.xml.XML

object Pom {
  def loadModel(tmpFile: TemporaryFile) = \/.fromTryCatch {
    val xml = XML.loadFile(tmpFile.file)
    scalaxb.fromXML[Model](xml)
  }

  def toXml(model: Model) = scalaxb.toXML(model, "project", org.scalajars.lib.maven.defaultScope)
}



case object WrongPath extends Exception
case object UnsupportedFileType extends Exception
case object InvalidPomFile extends Exception
case object UnknownException extends Exception
case object ProjectNotFound extends Exception
case class CombinedException(t1: Throwable, t2: Throwable) extends Exception

object Instances {
  implicit val ThrowableMonoid = new Monoid[Throwable] {
    def zero = UnknownException
    def append(f1: Throwable, f2: => Throwable) = CombinedException(f1, f2)
  }
}

import Instances._

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

case class ArtifactFile(group: List[String], name: String, version: String, fileName: String, fileType: ArtifactFileType.FileType, temporaryFile: TemporaryFile){
  lazy val path = pathParts.mkString("/")
  lazy val pathParts = (group ::: name :: version :: fileName :: Nil)
  lazy val artifactPath = (group :: name :: version :: Nil).mkString("/")
}

object ArtifactStore {
  import RedisStore._

  def resolveFileType(path: String) =
    ArtifactFileType.All.find(t => path.endsWith(t.ending)).toRightDisjunction(UnsupportedFileType)

  def getArtifactFile(path: String, tmpFile: TemporaryFile) = path.dropWhile('/'==).split('/').toList.reverse match {
    case fileName :: version :: name :: group =>
      resolveFileType(fileName).map(fileType => ArtifactFile(group.reverse, name, version, fileName, fileType, tmpFile))
    case _ => WrongPath.left
  }

  def upload(artifactFile: ArtifactFile) =
    \/.fromTryCatch(artifactFile.temporaryFile.moveTo(new File("/tmp/scalajars" + artifactFile.path), replace = true))

  def storeFileInformation(artifactFile: ArtifactFile) = \/.fromTryCatch(pool.withClient { redis =>
    ("index" /: artifactFile.pathParts){ case (key, x) =>
      redis.sadd(namespaced(key), x)
      key + "/" + x
    }
  })

  def saveProjectArtifact(name: String, version: String, file: ArtifactFile) = \/.fromTryCatch(pool.withClient { redis =>
    redis.sadd(namespaced("projects:" + name + ":" + version + ":artifacts"), file.fileName)
  })

  def fetchProjectNameAndVersion(file: ArtifactFile) = \/.fromTryCatch(pool.withClient { redis =>
    for {
      data <- redis.hgetall[String, String](namespaced("path-to-project:" + file.artifactPath))
      name <- data.get("name")
      version <- data.get("version")
    } yield {
      (name, version)
    }
  }).flatMap(_.toRightDisjunction(ProjectNotFound))

  def saveModel(path: String, model: Model, file: ArtifactFile) = \/.fromTryCatch(pool.withClient { redis =>
    (for {
      name        <- model.name
      groupId     <- model.groupId
      artifactId  <- model.artifactId
      version     <- model.version
      dependencies <- model.dependencies
    } yield {
      redis.hmset(namespaced("path-to-project:" + file.artifactPath), Map("name" -> name, "version" -> version))
      redis.hmset(namespaced("projects:" + name), Map("group" -> groupId))
      redis.sadd(namespaced("projects:" + name + ":versions"), version)

      for {
        dep           <- dependencies.dependency
        depGroupId    <- dep.groupId
        depArtifactId <- dep.artifactId
        depVersion    <- dep.version
        depScope      <- dep.scope
      } yield {
        redis.sadd(namespaced("projects:" + name + ":versions:" + version + ":dependencies"), List(depGroupId, depArtifactId, depVersion, depScope).mkString("###"))
      }

      (name, version)
    })
  }).flatMap(_.toRightDisjunction(InvalidPomFile))

  def publish(path: String, tmpFile: TemporaryFile) = resolveFileType(path).flatMap { _ match {
    case ArtifactFileType.Pom =>
      for {
        file            <- getArtifactFile(path, tmpFile)
        model           <- Pom.loadModel(tmpFile)
        (name, version) <- saveModel(path, model, file)
        _               <- upload(file)
        _               <- storeFileInformation(file)
        _               <- saveProjectArtifact(name, version, file)
      } yield ()

    case ArtifactFileType.MD5 | ArtifactFileType.SHA1 =>
      for {
        file            <- getArtifactFile(path, tmpFile)
        (name, version) <- fetchProjectNameAndVersion(file)
        _               <- upload(file)
        _               <- storeFileInformation(file)
      } yield ()

    case _ =>
      for {
        file            <- getArtifactFile(path, tmpFile)
        (name, version) <- fetchProjectNameAndVersion(file)
        _               <- upload(file)
        _               <- storeFileInformation(file)
        _               <- saveProjectArtifact(name, version, file)
      } yield ()
  }}
}

object RedisStore {
  val namespace = "scalajars"
  val pool = use[play.redisclient.RedisPlugin].pool

  def namespaced(key: String) = namespace + ":" + key

  def now(): Long = (new java.util.Date).getTime() / 1000 // in seconds
}

object Publish extends Controller {
  def put(path: String) = Action(parse.temporaryFile) { implicit request =>
    ArtifactStore.publish(path, request.body).fold(
      error => {
        Logger.error(error.toString)
        BadRequest(error.getMessage)

      },
      success => Ok
    )
  }

}

