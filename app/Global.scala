import org.scalajars.web.nav

import play.api._
import play.api.mvc._
// import play.api.Play.current

object Global extends GlobalSettings {

  override def onRouteRequest(request: RequestHeader) = {
    nav.onRouteRequest(request) orElse super.onRouteRequest(request)
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    nav.onHandlerNotFound(request)
  }

}
