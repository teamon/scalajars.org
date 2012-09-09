package org.scalajars.web.controllers

import org.scalajars.core._
import org.scalajars.web.lib._

import scalaz._, Scalaz._

import play.api._
import play.api.mvc._

object PublishController extends Controller with ControllerOps {
  def put(projectName: String, token: UserToken, path: Path) = Action(parse.temporaryFile) { implicit request =>
    Publisher.publish(token, projectName, path, request.body) ==> Ok
  }

}

