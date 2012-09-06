package org.scalajars.web.controllers

import play.api._
import play.api.mvc._



// trait HeadAwareAction[A] extends Action[A]


object Application extends Controller {
  def index() = Action { implicit request =>
    Ok(views.html.index())
  }

  val store = Map(1 -> "Item 1")

  def show(id: Int) = Action {
    Ok("")
    // store(id).map(i => LazyOk(i)) getOrElse NotFound
  }
}

