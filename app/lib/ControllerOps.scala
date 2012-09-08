package org.scalajars.web.lib

import org.scalajars.core.User
import org.scalajars.web.controllers.Users
import org.scalajars.web.nav

import scalaz._, Scalaz._
import play.api.mvc._

trait ControllerOps {
  this: Controller =>

  def UserAction(f: Request[AnyContent] => User => Result): Action[AnyContent] = Action { request =>
    getUserFromSession(request).fold(
      f(request),
      Redirect(nav.auth.signin())
    )
  }

  def OptUserAction(f: Request[AnyContent] => Option[User] => Result) = Action { request =>
    f(request)(getUserFromSession(request))
  }

  protected def getUserFromSession(request: Request[_]) = request.session.get("login").map(Users.getUser).map(_.toOption).join.join

  def params(key: String)(implicit request: RequestHeader) = request.queryString.get(key) >>= (_.headOption)
}
