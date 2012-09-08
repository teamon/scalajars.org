package org.scalajars.web

import org.scalajars.web.controllers._
import play.navigator._

object nav extends PlayNavigator {
  val home = GET on root to Application.index

  // val show = GET on "show" / * to Application.show
  // HEAD on "show" / * to Application.show

  val browse = GET on "browse" / ** to Browse.index
  val projects = GET on "projects" to Browse.projects
  val project = GET on "projects" / * to Browse.project

  PUT on "publish" / * / ** to Publish.put

  val auth = new Namespace("auth"){
    val signin = GET on "signin" to Auth.signin
    val signout = GET on "signout" to Auth.signout
    val callback = GET on "callback" to Auth.callback
  }


}
