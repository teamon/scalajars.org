package org.scalajars.core

import org.scalajars.core.ArtifactFileType._
import scalaz._, Scalaz._

import org.scalajars.lib.maven.Model
import scala.xml._

import play.api.libs.Files.TemporaryFile

object PomUtils {
  def readModel(xml: Elem): Result[Model] = \/.fromTryCatch(scalaxb.fromXML[Model](xml))
  def writeModel(model: Model) = scalaxb.toXML(model, "project", org.scalajars.lib.maven.defaultScope)

  def readXml(file: java.io.File): Result[Elem] = \/.fromTryCatch(XML.loadFile(file))

  def readProject(model: Model): Result[Project] = (for {
    name        <- model.name
    artifactId  <- model.artifactId
    groupId     <- model.groupId
    version     <- model.version
  } yield {
    Project(name, model.description | "", Artifact(artifactId, groupId, Version(version, Nil) :: Nil) :: Nil)
  }).toRightDisjunction(InvalidPomFile)

}

trait Publisher {
  this: Store =>

  import PomUtils._

  case class File(rawPath: String, tmpFile: TemporaryFile, fileType: FileType){
    lazy val basePath = rawPath.split('/').dropRight(1).mkString("/")
  }

  def apply(path: String, tmpFile: TemporaryFile) = for {
    typ   <- resolveFileType(path)
    res   <- process(File(path, tmpFile, typ))
  } yield res


  def process(file: File): Error \/ Unit = file.fileType match {
    case Pom => for {
      project <- (Kleisli(readProject) <==< readModel <==< readXml).run(file.tmpFile.file)
      // project <- readXml(file.tmpFile.file) >>= readModel >>= readProject
      _       <- setProject(project)
      _       <- setProjectByPath(project, file.basePath)
      _       <- upload(file)
    } yield ()

    case _ => for {
      project <- getProjectByPath(file.basePath)
      _       <- upload(file)
    } yield ()
  }

  def upload(file: File): Error \/ Unit = {
    ().right
  }

  def resolveFileType(path: String): Error \/ FileType =
    ArtifactFileType.All.find(t => path.endsWith(t.ending)).toRightDisjunction(UnsupportedFileType)
}
