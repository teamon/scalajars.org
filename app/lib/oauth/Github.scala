package org.scalajars.web.lib.oauth

import scalaz._, Scalaz._
import play.scalaz.json._

case class GithubUser(
  login: String,
  email: String,
  avatar_url: String,
  name: String
)

object GithubUser {
  implicit def GithubUserReadz = readz4("login", "email", "avatar_url", "name")(GithubUser.apply)
}

case class GithubOAuth2(clientId: String, clientSecret: String) extends OAuth2[GithubUser] {
  def oauthSignInUrl = "https://github.com/login/oauth/authorize"
  def oauthAccessTokenUrl = "https://github.com/login/oauth/access_token"
  def oauthUserInfoUrl = "https://api.github.com/user"

  def loadUser(body: String) = fromJsonString[GithubUser](body).toOption
}
