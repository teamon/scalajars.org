package org.scalajars.web.controllers

import org.scalajars.web.nav
import org.scalajars.web.lib._

import play.api.mvc._
import play.api.libs.json._


object Auth extends Controller {

  val GITHUB = new OAuth2[GithubUser](OAuth2Settings(
    "16b1cf5716436e9e0af5",
    "952765967dd1af893aefafc991e06519517f3c15",
    "https://github.com/login/oauth/authorize",
    "https://github.com/login/oauth/access_token",
    "https://api.github.com/user"
  )){
    def user(body: String) = Json.fromJson(Json.parse(body))
  }

  case class GithubUser(
    login: String,
    email: String,
    avatar_url: String,
    name: String
  )

  implicit def GithubUserReads: Reads[GithubUser] = new Reads[GithubUser]{
    def reads(json: JsValue) = GithubUser(
      (json \ "login").as[String],
      (json \ "email").as[String],
      (json \ "avatar_url").as[String],
      (json \ "name").as[String]
    )
  }

  def signin() = Action { Redirect(GITHUB.signIn) }

  def signout() = Action { Redirect(nav.home()).withSession() }

  def callback() = Action { implicit request =>
    params("code").flatMap { code =>
      GITHUB.authenticate(code) map { user =>
        Redirect(nav.home()).withSession("login" -> user.login)
      }
    } getOrElse Redirect(GITHUB.signIn)
  }

  protected def params[T](key: String)(implicit request: Request[T]) = request.queryString.get(key).flatMap(_.headOption)
}

