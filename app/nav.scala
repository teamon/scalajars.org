package org.scalajars.web

import org.scalajars.web.controllers._
import play.navigator._

object nav extends PlayNavigator {
  val home = GET on root to ApplicationController.index

  val index     = GET on "index" / **   to BrowseController.index
  val projects  = GET on "projects"     to BrowseController.projects
  val project   = GET on "projects" / * to BrowseController.project

  val publish   = PUT on "publish" / * / ** to PublishController.put

  val profile   = GET on "profile" to ProfileController.show

  val auth = new Namespace("auth"){
    val signin    = GET on "signin"   to AuthController.signin
    val signout   = GET on "signout"  to AuthController.signout
    val callback  = GET on "callback" to AuthController.callback
  }
}
