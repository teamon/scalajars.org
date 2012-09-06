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

  def storeFileInformation(model: Model, artifactFile: ArtifactFile) = \/.fromTryCatch(pool.withClient { redis =>
    ("index" /: artifactFile.pathParts){ case (key, x) =>
      redis.sadd(namespaced(key), x)
      key + "/" + x
    }
  })

  def saveModel(path: String, model: Model) = \/.fromTryCatch(pool.withClient { redis =>
    val artifactPath = path.split('/').dropRight(1).mkString("/")
    redis.set(namespaced("poms:" + artifactPath), Pom.toXml(model))

    for {
      name        <- model.name
      groupId     <- model.groupId
      artifactId  <- model.artifactId
      version     <- model.version
    } yield {
      redis.hmset(namespaced("projects:" + name), Map("group" -> groupId))
      redis.sadd(namespaced("projects:" + name + ":artifacts"), artifactId + ":" + version)
      redis.sadd(namespaced("projects"), name)
    }
  })

  def publish(path: String, tmpFile: TemporaryFile) = resolveFileType(path).flatMap { _ match {
    case ArtifactFileType.Pom =>
      for {
        file    <- getArtifactFile(path, tmpFile)
        model   <- Pom.loadModel(tmpFile)
        _       <- saveModel(path, model)
        _       <- upload(file)
        _       <- storeFileInformation(model, file)
      } yield ()

    case _ =>
      for {
        file    <- getArtifactFile(path, tmpFile)
        // model   <-
        // TODO: Validate file!
        _       <- upload(file)
        // _       <- storeFileInformation(file)
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
      error => BadRequest(error.getMessage),
      success => Ok
    )
  }

}

