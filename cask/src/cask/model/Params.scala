package cask.model

import java.io.{ByteArrayOutputStream, InputStream}

import cask.endpoints.ParamReader.NilParam
import cask.internal.Util
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.CookieImpl
import io.undertow.websockets.spi.WebSocketHttpExchange

class Subpath(val value: Seq[String])
object Subpath{
  implicit object SubpathParam extends NilParam[Subpath]((ctx, label) => new Subpath(ctx.remaining))
}

case class Request(exchange: HttpServerExchange){
  import collection.JavaConverters._
  lazy val cookies: Map[String, Cookie] = {
    exchange.getRequestCookies.asScala.mapValues(Cookie.fromUndertow).toMap
  }
  lazy val data: InputStream = exchange.getInputStream
  def readAllBytes() = {
    val baos = new ByteArrayOutputStream()
    Util.transferTo(data, baos)
    baos.toByteArray
  }
  lazy val queryParams: Map[String, Seq[String]] = {
    exchange.getQueryParameters.asScala.mapValues(_.asScala.toArray.toSeq).toMap
  }
  lazy val headers: Map[String, Seq[String]] = {
    exchange.getRequestHeaders.asScala
      .map{ header => header.getHeaderName.toString.toLowerCase -> header.asScala }
      .toMap
  }
}
object Request{
  implicit object RequestParam extends NilParam[Request]((ctx, label) => new Request(ctx.exchange))
}
object Cookie{
  implicit object CookieParam extends NilParam[Cookie]((ctx, label) =>
    Cookie.fromUndertow(ctx.exchange.getRequestCookies().get(label))
  )
  def fromUndertow(from: io.undertow.server.handlers.Cookie): Cookie = {
    Cookie(
      from.getName,
      from.getValue,
      from.getComment,
      from.getDomain,
      if (from.getExpires == null) null else from.getExpires.toInstant,
      from.getMaxAge,
      from.getPath,
      from.getVersion,
      from.isDiscard,
      from.isHttpOnly,
      from.isSecure
    )
  }
  def toUndertow(from: Cookie): io.undertow.server.handlers.Cookie = {
    val out = new CookieImpl(from.name, from.value)
    out.setComment(from.comment)
    out.setDomain(from.domain)
    out.setExpires(if (from.expires == null) null else java.util.Date.from(from.expires))
    out.setMaxAge(from.maxAge)
    out.setPath(from.path)
    out.setVersion(from.version)
    out.setDiscard(from.discard)
    out.setHttpOnly(from.httpOnly)
    out.setSecure(from.secure)
  }
}
case class Cookie(name: String,
                  value: String,
                  comment: String = null,
                  domain: String = null,
                  expires: java.time.Instant = null,
                  maxAge: Integer = null,
                  path: String = null,
                  version: Int = 1,
                  discard: Boolean = false,
                  httpOnly: Boolean = false,
                  secure: Boolean = false) {

}


sealed trait FormEntry{
  def valueOrFileName: String
  def headers: io.undertow.util.HeaderMap
  def asFile: Option[FormFile] = this match{
    case p: FormValue => None
    case p: FormFile => Some(p)
  }
}
object FormEntry{
  def fromUndertow(from: io.undertow.server.handlers.form.FormData.FormValue) = {
    if (!from.isFile) FormValue(from.getValue, from.getHeaders)
    else FormFile(from.getFileName, from.getPath, from.getHeaders)
  }

}
case class FormValue(value: String,
                     headers: io.undertow.util.HeaderMap) extends FormEntry{
  def valueOrFileName = value
}

case class FormFile(fileName: String,
                    filePath: java.nio.file.Path,
                    headers: io.undertow.util.HeaderMap) extends FormEntry{
  def valueOrFileName = fileName
}
