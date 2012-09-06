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

case class FullProject(name: String, groupId: String, artifacts: Set[String])

object ArtifactBrowser {
  import RedisStore._

  def projects = pool.withClient { redis =>
    redis.smembers(namespaced("projects")).map(_.flatten.map(Project)).flatten
  }

  def project(name: String) = pool.withClient { redis =>
    redis.hgetall(namespaced("project:" + name)).map { data =>
      FullProject(
        name,
        data.get("groupId") getOrElse "",
        redis.smembers(namespaced("projects:" + name + ":artifacts")).map(_.flatten).flatten.toSet
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

object Browse extends Controller {
  def index(path: String) = Action { implicit request =>
    val (artifacts, upPath) = ArtifactBrowser.index(path)
    Ok(views.html.browse.index(path, artifacts, upPath))
  }

  def projects() = Action { implicit request =>
    val projects = ArtifactBrowser.projects
    Ok(views.html.browse.projects(projects))
  }

  def project(name: String) = Action { implicit request =>
    ArtifactBrowser.project(name).map { project => Ok(views.html.browse.project(project)) } getOrElse NotFound
  }

}

