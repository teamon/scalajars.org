package org.scalajars.web.controllers

import org.scalajars.core._
import org.scalajars.web.nav
import org.scalajars.web.lib._
import org.scalajars.web.lib.oauth._

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Promise
import play.api.Play
import play.api.Play.current

import scalaz._, Scalaz._
import play.scalaz._


object AuthController extends Controller with ControllerOps {
  val github = new GithubOAuth2(
    Play.application.configuration.getString("oauth.github.clientId") | "",
    Play.application.configuration.getString("oauth.github.clientSecret") | ""
  )

  def signin() = Action { Redirect(github.signInUrl) }

  def signout() = Action { Redirect(nav.home()).withSession() }

  def callback() = Action { implicit request =>
    Async {
      (for {
        code <- OptionT(params("code").point[Promise])
        user <- OptionT(github.authenticate(code))
      } yield {
        user
      }).run.map(_.fold(
        user => {
          Users.saveUser(convertUser(user))
          Redirect(nav.home()).withSession("login" -> user.login)
        },
        Redirect(github.signInUrl)
      ))
    }
  }

  protected def convertUser(ghuser: GithubUser) = User(ghuser.login, ghuser.email, ghuser.name)
}

