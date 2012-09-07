package org.scalajars.core

import scalaz._, Scalaz._
import play.scalaz.json._


object formats {
  implicit val DependenctFormatz  = formatz3("groupId", "artifactId", "version")(Dependency)(Dependency.unapply(_).get)
  implicit val VersionFormatz     = formatz2("id", "dependencies")(Version)(Version.unapply(_).get)
  implicit val ArtifactFormatz    = formatz3("id", "groupId", "versions")(Artifact)(Artifact.unapply(_).get)
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

  implicit val ProjectParse = jsonObjectParse[Project]

  def listProjects() = withConnection { redis =>
    redis.smembers[String](key("projects" :: Nil)).map { _.flatten.flatMap { name =>
      redis.get[Project](key("projects" :: name :: Nil))
    } }.flatten.toList
  }

  def setProject(project: Project) = withConnection { redis =>
    redis.set(key("projects" :: project.name :: Nil), Json.stringify(toJson(project)))
    redis.sadd(key("projects" :: Nil), project.name)
  }

  def setProjectByPath(project: Project, path: String) = withConnection { redis =>
    redis.set(key("path-to-project" :: path :: Nil), project.name)
  }

  def getProject(name: String): Error \/ Project = (withConnection { redis =>
    redis.get[Project](key("projects" :: name :: Nil)).toRightDisjunction(ProjectNotFound)
  }).join

  def getProjectByPath(path: String): Error \/ Project = getProjectNameByPath(path) >>= getProject

  def getProjectNameByPath(path: String): Error \/ String = (withConnection { redis =>
    redis.get[String](key("path-to-project" :: path :: Nil)).toRightDisjunction(ProjectNotFound)
  }).join


  protected def key(xs: List[String]) = (namespace :: xs).mkString(":")

  protected def withConnection[A](f: RedisClient => A) = \/.fromTryCatch(pool.withClient(f))
}

case class RedisStore(namespace: String) extends RedisStoreImpl
