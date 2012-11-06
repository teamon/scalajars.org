package org.scalajars.core

import scalaz._, Scalaz._
import play.scalaz.json._
import play.api.Logger


object formats {
  implicit val DependenctFormatz  = formatz4("groupId", "artifactId", "version", "scope")(Dependency)(Dependency.unapply(_).get)
  implicit val ArtifactFormatz    = formatz2("id", "groupId")(Artifact(_: String, _: String, Nil, ArtifactFiles.empty))(a => (a.id, a.groupId))
  implicit val ScalaVersionFormatz = formatz1("id")(ScalaVersion(_: String, Nil))(_.id)
  implicit val VersionFormatz     = formatz1("id")(Version(_: String, Nil))(_.id)
  implicit val ProjectFormatz     = formatz3("name", "description", "user")(Project(_: String, _: String, _: String, Nil))(p => (p.name, p.description, p.user))
  implicit val ArtifactFilesFormatz = formatz5("pom", "jar", "war", "sources", "javadoc")(ArtifactFiles.apply)(ArtifactFiles.unapply(_).get)
  implicit val IndexItemFormatz: Formatz[IndexItem] = new Formatz[IndexItem]{
    def writes(item: IndexItem) = item match {
      case IndexFile(name, _) => toJson(Map("name" -> name, "_type" -> "file"))
      case IndexPackage(name, _) => toJson(Map("name" -> name, "_type" -> "package"))
    }

    def reads(js: play.api.libs.json.JsValue) = {
      val data = for {
        name  <- field[String]("name")
        _type <- field[String]("_type")
      } yield Apply[VA].apply(name, _type)((a,b) => (a,b) match {
        case (name, "file") => IndexFile(name, Path.root)
        case (name, "package") => IndexPackage(name, Path.root)
      })
      data(js)
    }
  }

  implicit val UserFormatz = formatz3("login", "email", "name")(User)(User.unapply(_).get)

}

object sorting {
  import scala.Ordering

  implicit val VersionOrdering: Ordering[Version] = Ordering.fromLessThan(_.idSplitted > _.idSplitted)
  implicit val IndexItemOrdering: Ordering[IndexItem] = Ordering.fromLessThan(_.name < _.name)
}

import sorting._

trait Store {
  def listProjects(): Error \/ List[Project]
  def setProject(project: Project, path: Path): Error \/ Unit
  def getProject(name: String): Error \/ Option[Project]
  def searchProjects(term: String): Error \/ List[Project]
  def setArtifactFiles(path: Path, files: ArtifactFiles): Error \/ Unit
  def addToIndex(item: IndexItem): Error \/ Unit
  def getIndex(path: Path): Error \/ List[IndexItem]
  def getUser(login: String): Error \/ Option[User]
  def setUser(user: User): Error \/ Unit
  def setUserToken(user: User, token: UserToken): Error \/ Unit
  def getUserToken(user: User): Error \/ Option[UserToken]
  def getUserByToken(token: UserToken): Error \/ Option[User]
}

trait RedisStoreImpl extends Store {
  import com.redis._
  import com.redis.serialization._
  import play.api.Play.current
  import play.api.libs.json.Json
  import org.scalajars.core.formats._

  case class RedisParserException(str: String) extends Exception {
    override def toString = "RedisParserException(" + str + ")"
  }

  def namespace: String

  protected val pool = use[play.redisclient.RedisPlugin].pool

  def jsonObjectParse[A:Readz]: Parse[A] = Parse { bytes =>
    fromJson[A](Json.parse(new String(bytes))) | (throw new RedisParserException(new String(bytes)))
  }

  // Required to not override Parse for primiteves
  implicit val ProjectParse = jsonObjectParse[Project]
  implicit val VersionParse = jsonObjectParse[Version]
  implicit val ScalaVersionParse = jsonObjectParse[ScalaVersion]
  implicit val ArtifactParse = jsonObjectParse[Artifact]
  implicit val DependencyParse = jsonObjectParse[Dependency]
  implicit val ArtifactFilesParse = jsonObjectParse[ArtifactFiles]
  implicit val IndexItemParse = jsonObjectParse[IndexItem]
  implicit val UserParse = jsonObjectParse[User]

  object keys {
    def projects =
      key("projects" :: Nil)
    def project(project: Project) =
      projectByName(project.name)
    def projectByName(name: String) =
      key("projects" :: name :: Nil)
    def versions(project: Project) =
      key("projects" :: project.name :: "versions" :: Nil)
    def scalaVersions(project: Project, version: Version) =
      key("projects" :: project.name :: "versions" :: version.id :: "scalaVersions" :: Nil)
    def artifacts(project: Project, version: Version, scalaVersion: ScalaVersion) =
      key("projects" :: project.name :: "versions" :: version.id :: "scalaVersions" :: scalaVersion.id :: "artifacts" :: Nil)
    def artifact(project: Project, version: Version, scalaVersion: ScalaVersion, artifact: Artifact) =
      key("projects" :: project.name :: "versions" :: version.id :: "scalaVersions" :: scalaVersion.id :: "artifacts" :: artifact.id :: Nil)
    def dependencies(project: Project, version: Version, scalaVersion: ScalaVersion, artifact: Artifact) =
      key("projects" :: project.name :: "versions" :: version.id :: "scalaVersions" :: scalaVersion.id :: "artifacts" :: artifact.id :: "dependencies" :: Nil)
    def artifactFiles(project: Project, version: Version, scalaVersion: ScalaVersion, artifact: Artifact) =
      key("projects" :: project.name :: "versions" :: version.id :: "scalaVersions" :: scalaVersion.id :: "artifacts" :: artifact.id :: "files" :: Nil)

    def projectsIndex(name: String) = key("projects-index" :: name :: Nil)
    val projectsIndexRoot = key("projects-index" :: "" :: Nil)

    def pathToArtifactFiles(path: Path) = key("path-to-artifact-files" :: path.list)
    def index(path: Path) = key("index" :: path.list)

    def user(user: User) = userByLogin(user.login)
    def userByLogin(login: String) = key("users" :: login :: Nil)
    def userToToken(user: User) = key("users-to-tokens" :: user.login :: Nil)
    def tokenToUser(token: UserToken) = key("tokens-to-users" :: token.token :: Nil)
  }

  def listProjects() = withConnection { redis =>
    redis.smembers[String](keys.projects).map { _.flatten.flatMap { name =>
      redis.get[Project](keys.projectByName(name))
    } }.flatten.toList
  }

  def setProject(project: Project, path: Path) = withConnection { redis =>
    redis.set(keys.project(project), toJsonString(project))
    redis.sadd(keys.projects, project.name)
    redis.set(keys.projectsIndex(project.name), "")

    project.versions.foreach { version =>
      redis.sadd(keys.versions(project), toJsonString(version))
      version.scalaVersions.foreach { scalaVersion =>
        redis.sadd(keys.scalaVersions(project, version), toJsonString(scalaVersion))
        scalaVersion.artifacts.foreach { artifact =>
          redis.sadd(keys.artifacts(project, version, scalaVersion), toJsonString(artifact))
          redis.set(keys.artifact(project, version, scalaVersion, artifact), toJsonString(artifact))
          artifact.dependencies.foreach { dependency =>
            redis.sadd(keys.dependencies(project, version, scalaVersion, artifact), toJsonString(dependency))
          }

          redis.set(keys.artifactFiles(project, version, scalaVersion, artifact), toJsonString(artifact.files))
          redis.set(keys.pathToArtifactFiles(path), keys.artifactFiles(project, version, scalaVersion, artifact))
        }
      }
    }
  }

  protected def getArtifactFilesPath(path: Path): Error \/ String = withConnection { redis =>
    redis.get[String](keys.pathToArtifactFiles(path)).toRightDisjunction(ArtifactNotFound)
  }.join

  protected def getArtifactFiles(path: String): Error \/ ArtifactFiles = withConnection { redis =>
    redis.get[ArtifactFiles](path).toRightDisjunction(ArtifactFilesNotFound)
  }.join

  def setArtifactFiles(path: Path, files: ArtifactFiles) = withConnection { redis =>
    for {
      filesPath <- getArtifactFilesPath(path)
      stored    <- getArtifactFiles(filesPath)
    } yield {
      redis.set(filesPath, toJsonString(stored.merge(files)))
    }
  }

  def getProject(name: String): Error \/ Option[Project] = (withConnection { redis =>
    redis.get[Project](keys.projectByName(name)).map { project =>
      project.copy(versions =
        redis.smembers[Version](keys.versions(project)).map { _.flatten.map { version =>
          version.copy(scalaVersions =
            redis.smembers[ScalaVersion](keys.scalaVersions(project, version)).map { _.flatten.map { scalaVersion =>
              scalaVersion.copy(artifacts =
                redis.smembers[Artifact](keys.artifacts(project, version, scalaVersion)).map { _.flatten.map { artifact =>
                  val dependencies = redis.smembers[Dependency](keys.dependencies(project, version, scalaVersion, artifact)).map(_.flatten).flatten.toList
                  val files = redis.get[ArtifactFiles](keys.artifactFiles(project, version, scalaVersion, artifact)) | ArtifactFiles.empty

                  artifact.copy(dependencies = dependencies, files = files)
                } }.flatten.toList
              )
            } }.flatten.toList
          )
        } }.flatten.toList.sorted
      )
    }
  })

  def searchProjects(term: String): Error \/ List[Project] = withConnection { redis =>
    redis.keys[String](keys.projectsIndex("*" + term + "*")).map { _.flatten.flatMap { key =>
      val name = key.replace(keys.projectsIndexRoot, "")
      redis.get[Project](keys.projectByName(name))
    } } | Nil
  }

  def addToIndex(item: IndexItem) = withConnection { redis =>
    redis.sadd(keys.index(item.path), toJsonString(item))
  }

  def getIndex(path: Path) = withConnection { redis =>
    redis.smembers[IndexItem](keys.index(path)).map(_.flatten.map(_.withPath(path)).toList.sorted).toRightDisjunction(IndexNotFound)
  }.join

  def parts(path: String) = path.dropWhile('/'==).split('/').dropWhile(""==).toList

  def setUser(user: User) = withConnection { redis =>
    redis.set(keys.user(user), toJsonString(user))
  }

  def getUser(login: String) = withConnection { redis =>
    redis.get[User](keys.userByLogin(login))
  }

  def setUserToken(user: User, token: UserToken) = withConnection { redis =>
    redis.set(keys.userToToken(user), token.token)
    redis.set(keys.tokenToUser(token), user.login)
  }

  def getUserToken(user: User) = withConnection { redis =>
    redis.get[String](keys.userToToken(user)).map(UserToken.apply)
  }

  def getUserByToken(token: UserToken) = withConnection { redis =>
    redis.get[String](keys.tokenToUser(token)).map(getUser) | UserNotFound.left
  }.join

  protected def key(xs: List[String]) = (namespace :: xs).mkString(":")

  protected def withConnection[A](f: RedisClient => A) = \/.fromTryCatch(pool.withClient(f))
}
