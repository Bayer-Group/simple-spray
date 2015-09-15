package demo

import akka.actor.Actor
import spray.routing.HttpService
import scala.concurrent._

class SampleServiceActor extends Actor with SampleRoute {
    def actorRefFactory = context

    def receive = runRoute(route)
}

trait SampleRoute extends HttpService {
    import spray.httpx.SprayJsonSupport._
    import Stuff._
    import spray.http.MediaTypes
    implicit def executionContext: ExecutionContextExecutor = actorRefFactory.dispatcher

    val route = {
        path("stuff") {
            respondWithMediaType(MediaTypes.`application/json`) {
                get {
                    complete(Stuff(1, "my stuff"))
                } ~
                  post {
                      entity(as[Stuff]) { stuff =>
                          complete(Stuff(stuff.id + 100, stuff.data + " posted"))
                      }
                  }
            }
        } ~
        path("params") {
            get {
                parameters('req, 'opt.?) { (req, opt) =>
                    complete(s"Req: $req, Opt: $opt")
                }
            }
        } ~
        path("headers") {
            get {
                headerValueByName("ct-remote-user") { userId =>
                    complete(userId)
                }
            }
        } ~
        path("reactive") {
            get {
                complete(Future { "I'm reactive!" })
            }
        } ~
        pathPrefix("junk") {
            pathPrefix("mine") {
                pathEnd {
                    get {
                        complete("MINE!")
                    }
                }
            } ~ pathPrefix("yours") {
                pathEnd {
                    get {
                        complete("YOURS!")
                    }
                }
            }
        } ~
          get {
              complete("I exist!")
          }
    }
}

