package blueeyes.core.service

import scala.collection.JavaConversions._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.jboss.netty.handler.codec.http.HttpHeaders._
import blueeyes.util.FutureImplicits
import Converters._
import org.jboss.netty.handler.codec.http.{CookieEncoder, CookieDecoder, HttpRequest => NettyHttpRequest, HttpResponse => NettyHttpResponse, DefaultHttpResponse, HttpVersion => NettyHttpVersion}

import blueeyes.core.http.{HttpMethod, HttpStatusCodes, HttpRequest, HttpResponse, HttpStatus}
import org.jboss.netty.handler.codec.http._
import blueeyes.util.Future
import net.lag.logging.Logger
import org.jboss.netty.channel._

class NettyRequestHandler(hierarchies: List[RestHierarchy[_]]) extends SimpleChannelUpstreamHandler{
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request        = e.getMessage().asInstanceOf[NettyHttpRequest]
    val method         = fromNettyMethod(request.getMethod())
    val requestUri     = new QueryStringDecoder(request.getUri).getPath

    val builders       = hierarchies.map(new RequestBuilder(e, _))
    val builder        = builders.find(_.isDefinedAt((requestUri, method))).getOrElse(new NotFoundBuilder())

    builder((requestUri, method)).deliverTo(writeResponse(e) _)
  }

  private def writeResponse(e: MessageEvent)(response: NettyHttpResponse){
    val request   = e.getMessage().asInstanceOf[NettyHttpRequest]
    val keepAlive = isKeepAlive(request)
    
    if (keepAlive) response.setHeader(Names.CONTENT_LENGTH, response.getContent().readableBytes())

    val future = e.getChannel().write(response)

    if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE)
  }

  private def encodeCookie(request: NettyHttpRequest, response: NettyHttpResponse) = {
    val cookieString = request.getHeader(Names.COOKIE)
    if (cookieString != null) {
      val cookieDecoder = new CookieDecoder()
      val cookieEncoder = new CookieEncoder(true)
      val cookies       = cookieDecoder.decode(cookieString)
      cookies.foreach(cookieEncoder.addCookie(_))
      response.addHeader(Names.SET_COOKIE, cookieEncoder.encode())
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {}
}

class NotFoundBuilder extends Function1[(String, HttpMethod), Future[NettyHttpResponse]]{
  def apply(uriAndMethod: (String, HttpMethod)) = new Future[NettyHttpResponse]().deliver(new DefaultHttpResponse(NettyHttpVersion.HTTP_1_1, toNettyStatus(HttpStatus(HttpStatusCodes.NotFound))))
}

class RequestBuilder[S](event: MessageEvent, hierarchy: RestHierarchy[S]) extends PartialFunction[(String, HttpMethod), Future[NettyHttpResponse]]{
  private val log = Logger.get
  
  def isDefinedAt(uriAndMethod: (String, HttpMethod)) = findPattern(uriAndMethod._1, uriAndMethod._2).map(v => true).getOrElse(false)
  def apply(uriAndMethod: (String, HttpMethod)) = createResponse(uriAndMethod._1, event, findPattern(uriAndMethod._1, uriAndMethod._2).get)

  private def createResponse[T, S](requestUri: String, event: MessageEvent, handler: (RestPathPattern, HttpMethod, HttpRequest[T] => Future[HttpResponse[T]], HttpDataTranscoder[T, S])) = {
    try {
      val request = event.getMessage().asInstanceOf[NettyHttpRequest]
      val parameters = handler._1(requestUri)

      handler._3(fromNettyRequest(request, parameters, event.getRemoteAddress, handler._4)).map(response => {
        try {
          toNettyResponse(response, handler._4)
        }
        catch {
          case e: Throwable => log.error(e, "Error while NettyResponse creation. Request:" + event);  errorResponse(e.getMessage);
        }
      })
    }
    catch {
      case e: Throwable => log.error(e, "Error while request handling. Request:" + event); new Future[NettyHttpResponse]().deliver(errorResponse(e.getMessage));
    }
  }

  private def errorResponse(reason: String): NettyHttpResponse = new DefaultHttpResponse(NettyHttpVersion.HTTP_1_1, toNettyStatus(HttpStatus(HttpStatusCodes.InternalServerError, reason.replace("\n", ""))))

  private def findPattern(uri: String, method: HttpMethod) = hierarchy.hierarchy.find(handler => handler._1.isDefinedAt(uri) && method == handler._2)
}