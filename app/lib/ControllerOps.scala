package org.scalajars.web.lib

import org.scalajars.core._
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

  implicit def EitherUnitToResult(dis: Error \/ Unit) = new {
    def ==>[R <: Result](f: R) = dis.fold(e => BadRequest(e.toString), _ => f)
  }

  implicit def EitherOptionToResultFun[A](dis: Error \/ Option[A]) = new {
    def ==>[R <: Result](f: A => R) = dis.fold(
      e => BadRequest(e.toString),
      _.fold(f, NotFound)
    )
  }

  implicit def EitherToResultFun[A](dis: Error \/ A) = new {
    def ==>[R <: Result](f: A => R) = dis.fold(e => BadRequest(e.toString), f)
  }


}
