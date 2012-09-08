package org.scalajars.web.controllers

import org.scalajars.web.lib._

import play.api._
import play.api.mvc._

import scalaz._
import Scalaz._









case class BrowserItem(path: String, name: String){
  def fullPath = (path + "/" + name).dropWhile('/'==)
}

case class Project(name: String){
  def fullPath = name
}


case class Dependency(groupId: String, artifactId: String, version: String, scope: String)
case class ProjectVersion(id: String, artifacts: List[String], dependencies: List[Dependency])
case class FullProject(name: String, groupId: String, versions: List[ProjectVersion])

object PrintDependency {
  def sbt(project: FullProject, version: ProjectVersion) =
    """libraryDependencies += "%s" %%%% "%s" %% "%s" """.format(project.groupId, project.name, version.id)
}

object ArtifactBrowser {
  import RedisStore._

  def projects = pool.withClient { redis =>
    redis.smembers(namespaced("projects")).map(_.flatten.map(Project)).flatten
  }

  def project(name: String) = pool.withClient { redis =>
    redis.hgetall(namespaced("projects:" + name)).map { data =>
      val versions = redis.smembers(namespaced("projects:" + name + ":versions")).map(_.flatten).flatten.toList.sorted.reverse.map { id: String =>
        val deps = redis.smembers(namespaced("projects:" + name + ":versions:" + id + ":dependencies")).map { _.flatten.map(_.split("###").toList).collect {
          case depGroupId :: depArtifactId :: depVersion :: depScope :: Nil => Dependency(depGroupId, depArtifactId, depVersion, depScope)
        } }.flatten.toList

        ProjectVersion(id, redis.smembers(namespaced("projects:" + name + ":" + id + ":artifacts")).map(_.flatten).flatten.toList.sorted, deps)
      }

      FullProject(
        name,
        data.get("group") getOrElse "",
        versions
      )
    }

  }

  def index(path: String) = {
    val (upPath, correctPath) = if(path == "/") (None, "") else (Some(path.split('/').dropRight(1).mkString("/").dropWhile('/'==)), path)
    val key = "index" + correctPath

    val artifacts = pool.withClient { redis =>
      redis.smembers(namespaced(key)).map { _.flatten.toList.map { name =>
        BrowserItem(correctPath, name)
      } }.flatten
    }

    (artifacts, upPath)
  }
}

import org.scalajars.core._

object Browser extends Browser with RedisStoreImpl {
  def namespace = "scalajars"
}

object Browse extends Controller {
  def index(path: String) = Action { implicit request =>
    val (artifacts, upPath) = ArtifactBrowser.index(path)
    Ok(views.html.browse.index(path, artifacts, upPath))
  }

  def projects() = Action { implicit request =>
    Browser.projects.fold(
      e => BadRequest(e.toString),
      p => Ok(views.html.browse.projects(p))
    )
  }

  def project(name: String) = Action { implicit request =>
    Browser.project(name).fold(
      e => BadRequest(e.toString),
      p => Ok(views.html.browse.project(p))
    )
  }

}

