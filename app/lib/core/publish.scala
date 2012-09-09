package org.scalajars.core

import org.scalajars.core.ArtifactFileType._
import scalaz._, Scalaz._

import org.scalajars.lib.maven.Model
import scala.xml._

import play.api.libs.Files.TemporaryFile
import play.api.Logger
import play.api.Play.{application, current}

object PomUtils {
  def readModel(xml: Elem): Res[Model] = \/.fromTryCatch(scalaxb.fromXML[Model](xml))

  def writeModel(model: Model) = scalaxb.toXML(model, "project", org.scalajars.lib.maven.defaultScope)

  def readXml(file: java.io.File): Res[Elem] = \/.fromTryCatch(XML.loadFile(file))
}

trait Publisher {
  this: Store with Users =>

  import PomUtils._

  case class File(rawPath: String, tmpFile: TemporaryFile, fileType: FileType){
    lazy val basePath = rawPath.split('/').dropRight(1).mkString("/")
  }

  def publish(token: UserToken, projectName: String, path: Path, tmpFile: TemporaryFile) = for {
    user      <- authorize(token, projectName)
    fileType  <- getFileType(path)
    res       <- fileType match {
      case Pom => for {
        model   <- (Kleisli(readModel) <==< readXml).run(tmpFile.file)
        version <- getVersion(path, fileType, model)
        _       <- setProject(Project(projectName, model.description | "", user.login, version :: Nil), path.base)
        _       <- upload(path, tmpFile)
        r       <- addPathToIndex(path)
      } yield r

      case _ => for {
        _   <- setArtifactFiles(path.base, ArtifactFiles.forPath(path, fileType))
        _   <- upload(path, tmpFile)
        r   <- addPathToIndex(path)
      } yield r
    }

  } yield res

  def authorize(token: UserToken, projectName: String): Error \/ User = (for {
    userOpt    <- getUserByToken(token)
    projectOpt <- getProject(projectName)
  } yield (userOpt, projectOpt)) >>= (_ match {
    case (None, _) => UserNotFound.left
    case (Some(user), Some(project)) if project.user != user.login => Unauthorized.left
    case (Some(user), _) => user.right
  })

  val uploadDir = application.configuration.getString("upload.dir") | "/tmp/scalajars"
  def uploadFile(path: Path) = new java.io.File(new java.io.File(uploadDir), path.str)

  def upload(path: Path, tmpFile: TemporaryFile): Error \/ Unit = {
    Logger.trace("Uploading " + path)
    tmpFile.moveTo(uploadFile(path), replace = true)
    ().right
  }

  def addPathToIndex(path: Path): Error \/ Unit = for {
    (base, last)  <- path.baseAndLast.toRightDisjunction[Error](WrongPath)
    _             <- makeIndexItems(base, last).map(addToIndex).sequence[Res, Unit]
  } yield ()

  def makeIndexItems(base: Path, last: String) = {
    val (_, pkg) = ((Path.root, List[IndexItem]()) /: base.parts){ case ((p, xs), x) =>
      (p / x, IndexPackage(x, p) :: xs)
    }
    IndexFile(last, base) :: pkg
  }

  def getVersion(path: Path, fileType: ArtifactFileType.FileType, model: Model): Error \/ Version = path.reversed.list match {
    case fileName :: version :: ArtifactId(artifactId, scalaVersion) :: groupId =>
      Version(version, ScalaVersion(scalaVersion, Artifact(artifactId, groupId.reverse.mkString("."), extractDependencies(model), ArtifactFiles.forPath(path, fileType)) :: Nil) :: Nil).right
    case _ => WrongPath.left
  }

  def extractDependencies(model: Model): List[Dependency] = {
    model.dependencies.map { _.dependency.map { dep =>
      Dependency(dep.groupId, dep.artifactId, dep.version, dep.scope)
    }.flatten }.flatten.toList
  }

  def getFileType(path: Path): Error \/ FileType =
    ArtifactFileType.All.find(t => path.endsWith(t.ending)).toRightDisjunction(UnsupportedFileType)
}
