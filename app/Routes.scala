import play.navigator._
import controllers._

trait RoutesDefinition extends PlayNavigator {
  val home = GET on root to Application.index

  val auth = new Namespace("auth"){
    val signin = GET on "signin" to Auth.signin
    val signout = GET on "signout" to Auth.signout
    val callback = GET on "callback" to Auth.callback
  }

  val assets = GET on "assets" / ** to { s: String => Assets.at(path="/public", s) }
}

package controllers {
    object routes extends RoutesDefinition
}

object Routes extends RoutesDefinition

