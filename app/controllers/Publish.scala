package org.scalajars.web.controllers

import org.scalajars.core._
import org.scalajars.web.lib._

import scalaz._, Scalaz._

import play.api._
import play.api.mvc._

object PublishController extends Controller {
  def put(projectName: String, path: Path) = Action(parse.temporaryFile) { implicit request =>
    Publisher(projectName, path, request.body).fold(
      error => {
        Logger.error(error.toString)
        BadRequest(error.getMessage)
      },
      success => Ok
    )
  }

}

