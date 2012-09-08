package org.scalajars.web.controllers

import org.scalajars.web.lib._

import play.api._
import play.api.mvc._

object ApplicationController extends Controller with ControllerOps {
  def index() = OptUserAction { implicit request => implicit user =>
    Ok(views.html.index())
  }
}

