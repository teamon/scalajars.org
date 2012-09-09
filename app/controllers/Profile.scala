package org.scalajars.web.controllers

import org.scalajars.core._
import org.scalajars.web.nav
import org.scalajars.web.lib._

import play.api._
import play.api.mvc._

import scalaz._, Scalaz._

object ProfileController extends Controller with ControllerOps {
  def show() = UserAction { implicit request => implicit user =>
    Users.getUserToken(user) ==> (token => Ok(views.html.profile(token)))
  }
}

