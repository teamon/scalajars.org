package org.scalajars.web.controllers

import org.scalajars.core._
import org.scalajars.web.lib._

import scalaz._, Scalaz._

import play.api._
import play.api.mvc._
import play.api.libs._
import play.api.libs.iteratee._

object PublishController extends Controller with ControllerOps {
  def put(projectName: String, token: UserToken, path: Path) = Action(parse.temporaryFile) { implicit request =>
    Publisher.publish(token, projectName, path, request.body) ==> Ok
  }

  def head(path: Path) = Action { implicit request =>
    fetchFile(path) ==> { case (headers, _) => Ok.withHeaders(headers.toSeq:_*) }
  }

  def get(path: Path) = Action { implicit request =>
    fetchFile(path) ==> { case (headers, data) => SimpleResult(header = ResponseHeader(OK, headers), data) }
  }

  def fetchFile(path: Path) = {
    val resource = (Path(Publisher.uploadDir) / path).absolute
    val file = new java.io.File(resource)

    if(file.isDirectory || !file.exists){
      ArtifactNotFound.left
    } else {
      val url = new java.net.URL("file://" + resource)

      \/.fromTryCatch {
        val stream = url.openStream()
        val length = stream.available
        val headers = Map(
          CONTENT_LENGTH -> length.toString,
          CONTENT_TYPE -> MimeTypes.forFileName(resource).getOrElse(BINARY)
        )

        (headers, Enumerator.fromStream(stream))
      }
    }
  }
}
