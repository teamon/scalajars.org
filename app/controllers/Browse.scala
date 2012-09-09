package org.scalajars.web.controllers

import org.scalajars.web.lib._
import org.scalajars.core._

import play.api._
import play.api.mvc._

import scalaz._
import Scalaz._

object BrowseController extends Controller with ControllerOps {
  def index(path: Path) = OptUserAction { implicit request => implicit user =>
    Browser.index(path) ==> (s => Ok(views.html.browse.index(path, s._1, s._2)))
  }

  def projects() = OptUserAction { implicit request => implicit user =>
    val searchQuery = params("query")
    (searchQuery.map(Browser.search) | Browser.projects) ==> (p => Ok(views.html.browse.projects(p, searchQuery)))
  }

  def project(name: String) = OptUserAction { implicit request => implicit user =>
    Browser.project(name) ==> (p => Ok(views.html.browse.project(p)))
  }

}

