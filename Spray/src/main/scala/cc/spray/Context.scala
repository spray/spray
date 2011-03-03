package cc.spray

import http.{HttpResponse, HttpRequest}

case class Context(request: HttpRequest, respond: HttpResponse => Unit)