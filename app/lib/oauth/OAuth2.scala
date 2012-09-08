package org.scalajars.web.lib.oauth

import play.api.libs.ws.WS
import play.core.parsers._
import play.api.libs.concurrent._

import scalaz._, Scalaz._
import play.scalaz._

trait OAuth2[T]{
  def clientId: String
  def clientSecret: String

  def oauthSignInUrl: String
  def oauthAccessTokenUrl: String
  def oauthUserInfoUrl: String

  def loadUser(body: String): Option[T]

  lazy val signInUrl = oauthSignInUrl + "?client_id=" + clientId

  def authenticate(code: String): Promise[Option[T]] = (for {
    accessToken <- OptionT(requestAccessToken(code))
    userInfo    <- OptionT(requestUserInfo(accessToken))
  } yield userInfo).run

  protected def requestAccessToken(code: String): Promise[Option[String]] =
    WS.url(requestAccessTokenUrl(code)).get.map { response =>
      FormUrlEncodedParser.parse(response.body).get("access_token") >>= (_.headOption)
    }

  protected def requestUserInfo(accessToken: String): Promise[Option[T]] =
    WS.url(requestUserInfoUrl(accessToken)).get.map(r => loadUser(r.body))

  protected def requestAccessTokenUrl(code: String) =
    oauthAccessTokenUrl +
      "?client_id=" + clientId +
      "&client_secret=" + clientSecret +
      "&code=" + code

  protected def requestUserInfoUrl(accessToken: String) =
    oauthUserInfoUrl + "?access_token=" + accessToken

}



