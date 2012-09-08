package org.scalajars.core

import scalaz._, Scalaz._
import play.scalaz.json._


object formats {
  implicit val DependenctFormatz  = formatz4("groupId", "artifactId", "version", "scope")(Dependency)(Dependency.unapply(_).get)
  implicit val ArtifactFormatz    = formatz2("id", "groupId")(Artifact(_: String, _: String, Nil))(a => (a.id, a.groupId))
  implicit val ScalaVersionFormatz = formatz1("id")(ScalaVersion(_: String, Nil))(_.id)
  implicit val VersionFormatz     = formatz1("id")(Version(_: String, Nil))(_.id)
  implicit val ProjectFormatz     = formatz2("name", "description")(Project(_: String, _: String, Nil))(p => (p.name, p.description))
}

trait Store {
  def listProjects(): Error \/ List[Project]
  def setProject(project: Project): Error \/ Unit
  def setProjectByPath(project: Project, path: String): Error \/ Unit
  def getProject(name: String): Error \/ Project
  def getProjectByPath(path: String): Error \/ Project
}

trait RedisStoreImpl extends Store {
  import com.redis._
  import com.redis.serialization._
  import play.api.Play.current
  import play.api.libs.json.Json
  import org.scalajars.core.formats._

  class RedisParserException extends Exception

  def namespace: String

  protected val pool = use[play.redisclient.RedisPlugin].pool

  def jsonObjectParse[A:Readz]: Parse[A] = Parse { bytes =>
    fromJson[A](Json.parse(new String(bytes))) | (throw new RedisParserException)
  }

  // Required to not override Parse for primiteves
  implicit val ProjectParse = jsonObjectParse[Project]
  implicit val VersionParse = jsonObjectParse[Version]
  implicit val ScalaVersionParse = jsonObjectParse[ScalaVersion]
  implicit val ArtifactParse = jsonObjectParse[Artifact]
  implicit val DependencyParse = jsonObjectParse[Dependency]

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
    def dependencies(project: Project, version: Version, scalaVersion: ScalaVersion, artifact: Artifact) =
      key("projects" :: project.name :: "versions" :: version.id :: "scalaVersions" :: scalaVersion.id :: "artifacts" :: artifact.id :: "dependencies" :: Nil)

    def pathToProject(path: String) = key("path-to-project" :: path :: Nil)
  }

  def listProjects() = withConnection { redis =>
    redis.smembers[String](keys.projects).map { _.flatten.flatMap { name =>
      redis.get[Project](keys.projectByName(name))
    } }.flatten.toList
  }

  def setProject(project: Project) = withConnection { redis =>
    redis.set(keys.project(project), toJsonString(project))
    redis.sadd(keys.projects, project.name)

    project.versions.foreach { version =>
      redis.sadd(keys.versions(project), toJsonString(version))
      version.scalaVersions.foreach { scalaVersion =>
        redis.sadd(keys.scalaVersions(project, version), toJsonString(scalaVersion))
        scalaVersion.artifacts.foreach { artifact =>
          redis.sadd(keys.artifacts(project, version, scalaVersion), toJsonString(artifact))
          artifact.dependencies.foreach { dependency =>
            redis.sadd(keys.dependencies(project, version, scalaVersion, artifact), toJsonString(dependency))
          }
        }
      }
    }
  }

  def setProjectByPath(project: Project, path: String) = withConnection { redis =>
    redis.set(keys.pathToProject(path), project.name)
  }

  def getProject(name: String): Error \/ Project = (withConnection { redis =>
    redis.get[Project](keys.projectByName(name)).map { project =>
      project.copy(versions =
        redis.smembers[Version](keys.versions(project)).map { _.flatten.map { version =>
          version.copy(scalaVersions =
            redis.smembers[ScalaVersion](keys.scalaVersions(project, version)).map { _.flatten.map { scalaVersion =>
              scalaVersion.copy(artifacts =
                redis.smembers[Artifact](keys.artifacts(project, version, scalaVersion)).map { _.flatten.map { artifact =>
                  artifact.copy(dependencies =
                    redis.smembers[Dependency](keys.dependencies(project, version, scalaVersion, artifact)).map(_.flatten).flatten.toList
                  )
                } }.flatten.toList
              )
            } }.flatten.toList
          )
        } }.flatten.toList
      )
    }.toRightDisjunction(ProjectNotFound)
  }).join

  def getProjectByPath(path: String): Error \/ Project = getProjectNameByPath(path) >>= getProject

  def getProjectNameByPath(path: String): Error \/ String = (withConnection { redis =>
    redis.get[String](keys.pathToProject(path)).toRightDisjunction(ProjectNotFound)
  }).join

  def toJsonString[A:Writez](a: A) = Json.stringify(toJson(a))

  protected def key(xs: List[String]) = (namespace :: xs).mkString(":")

  protected def withConnection[A](f: RedisClient => A) = \/.fromTryCatch(pool.withClient(f))
}

case class RedisStore(namespace: String) extends RedisStoreImpl
