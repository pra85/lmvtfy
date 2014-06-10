package com.getbootstrap.lmvtfy.server

import scala.util.{Try, Success, Failure}
import akka.actor.Actor
import spray.routing._
import spray.routing.directives.DebuggingDirectives
import spray.http._
import com.getbootstrap.lmvtfy.util.Utf8String
import akka.event.Logging

class LmvtfyActor extends Actor with Lmvtfy {
  def actorRefFactory = context

  // TODO: timeout handling
  def receive = runRoute(theOnlyRoute)
}

trait Lmvtfy extends HttpService {
  import GitHubIssuesWebHooksDirectives.authenticatedIssueOrCommentEvent

  val theOnlyRoute =
    DebuggingDirectives.logRequestResponse("get-user", Logging.InfoLevel){
    path("lmvtfy") {
      post {
        headerValueByName("X-Github-Event") { githubEvent =>
          headerValueByName("X-Hub-Signature") { hmacStr =>
            val parts = hmacStr.split('=')
            parts match {
              case Array("sha1", hmacHex) => {
                Try{ javax.xml.bind.DatatypeConverter.parseHexBinary(hmacHex) } match {
                  case Failure(_) => complete(StatusCodes.Forbidden, "Malformed HMAC hex!")
                  case Success(hmacBytes) => {
                    githubEvent match {
                      case "ping" => {
                        System.out.println("Pong.")
                        complete(StatusCodes.OK)
                      }
                      case "issues" | "issue_comment" => {
                        val secretKey = "abcdefg".utf8Bytes // FIXME
                        authenticatedIssueOrCommentEvent(secretKey) { event =>
                          event.action match {
                            case "opened" | "created" => {
                              System.out.println("EVENT: ", event)
                              // FIXME: DO ACTUAL WORK
                              complete(StatusCodes.OK)
                            }
                            case _ => complete(StatusCodes.OK, "Ignoring irrelevant action")
                          }
                        }
                      }
                      case _ => complete(StatusCodes.BadRequest, "Unexpected event type")
                    }
                  }
                }
              }
              case _ => complete(StatusCodes.Forbidden, "Malformed HMAC!")
            }
          }
        }
      }
    }
    }
}
