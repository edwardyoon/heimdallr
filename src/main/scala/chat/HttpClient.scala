/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package chat

import scala.util.{Failure, Success, Try}
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.util.ByteString
import scala.concurrent.{ExecutionContext, Future}
import chat.HttpClient._
import UserActor.CustomResponse

object HttpClient {
  case class HttpClientGet(event: String, path : String)
  case class HttpClientPost(event: String, path : String, token: String, jsonBody: String)
  case class HttpClientResponseSuccess(event: String, resHttp: HttpResponse, recipient: ActorRef)
  case class HttpClientResponseFailure(event: String, reason: String, recipient: ActorRef)
}

class HttpClient()(implicit system: ActorSystem, mat: ActorMaterializer, dispatcher: ExecutionContext)
  extends Actor with ActorLogging {

  def pipeToSelf(event: String, future: Future[HttpResponse], recipient: ActorRef): Future[HttpResponse] = {
    future andThen {
      case Success(r) => self ! HttpClientResponseSuccess(event, r, recipient)
      case Failure(f) => self ! HttpClientResponseFailure(event, f.toString, recipient)
    }
  }

  def post(event: String, path: String, token: String, jsonBody: String, recipient: ActorRef) = {
    val objectEntity = HttpEntity(ContentTypes.`application/json`, jsonBody)
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = path,
      entity = objectEntity
      ).withHeaders(
        RawHeader("Authorization", "Token " + token)
      )
    )

    pipeToSelf(event, responseFuture, recipient)

  }

  def get(event: String, path: String, recipient:ActorRef) = {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = path
      )
    )

    pipeToSelf(event, responseFuture, recipient)
  }

  def receive = {
    case HttpClientGet(event, path) =>
      get(event, path, sender)

    case HttpClientPost(event, path, token, jsonBody) =>
      post(event, path, token, jsonBody, sender)

    // connection success
    case HttpClientResponseSuccess(event, resp, recipient) =>
      resp match {
        case HttpResponse(StatusCodes.OK, headers, entity, _) =>
          entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
            log.info("Got response, body: " + body.utf8String)
            recipient ! CustomResponse(event, 200, body.utf8String)
          }

        case resp @ HttpResponse(code, _, _, _) =>
          log.info("Request failed, response code: " + code)
          resp.discardEntityBytes()
          recipient ! CustomResponse(event, code.intValue(), s"Request failed, response code: $code")
      }

    // connection failure
    case HttpClientResponseFailure(event, resp, recipient) =>
      log.info("Request failed, reason: " + resp)
      recipient ! CustomResponse(event, 599, s"Request failed, response code: ${resp}")

    case x =>
      log.info(s"HttpClient Request failed: ${x}")
  }

  override def preStart(): Unit = {
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info( reason.toString )
  }

  override def postStop(): Unit = {
  }
}