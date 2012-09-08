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

  // def readProject(model: Model): Result[(String, String, String, String)] = (for {
  //   artifactId  <- model.artifactId
  //   groupId     <- model.groupId
  //   version     <- model.version
  // } yield {
  //   (groupId, artifactId, version, model.description | "")
  // }).toRightDisjunction(InvalidPomFile)

}

trait Publisher {
  this: Store =>

  import PomUtils._

  case class File(rawPath: String, tmpFile: TemporaryFile, fileType: FileType){
    lazy val basePath = rawPath.split('/').dropRight(1).mkString("/")
  }

  def apply(projectName: String, path: String, tmpFile: TemporaryFile) = for {
    fileType  <- getFileType(path)
    _         <- fileType match {
      case Pom => for {
        model   <- (Kleisli(readModel) <==< readXml).run(tmpFile.file)
        version <- getVersion(path, model)
        project = Project(projectName, model.description | "", version :: Nil)

        _       <- setProject(project)
        _       <- setProjectByPath(project, basePath(path))
        // _       <- upload(file)
      } yield ()

      case _ => for {
        project <- getProjectByPath(basePath(path))
        // _       <- upload(file)
      } yield ()
    }

  } yield ()

  def basePath(path: String) = path.split('/').dropRight(1).mkString("/")

  def upload(file: File): Error \/ Unit = {
    ().right
  }

  def getVersion(path: String, model: Model): Error \/ Version = path.dropWhile('/'==).split('/').reverse.toList match {
    case fileName :: version :: ArtifactId(artifactId, scalaVersion) :: groupId =>
      Version(version, ScalaVersion(scalaVersion, Artifact(artifactId, groupId.reverse.mkString("."), extractDependencies(model)) :: Nil) :: Nil).right
    case _ => WrongPath.left
  }

  def extractDependencies(model: Model): List[Dependency] = {
    model.dependencies.map { _.dependency.map { dep =>
      Dependency(dep.groupId, dep.artifactId, dep.version, dep.scope)
    }.flatten }.flatten.toList
  }

  def getFileType(path: String): Error \/ FileType =
    ArtifactFileType.All.find(t => path.endsWith(t.ending)).toRightDisjunction(UnsupportedFileType)
}
