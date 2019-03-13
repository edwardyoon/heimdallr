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
package chat.utils

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import chat.environment
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Implementation of GET and POST request methods
  */
object ClientHttp {

  implicit val system = environment.getHeimdallrSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  def post(url: String, token: String):Future[String] = {

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = url
      ).withHeaders(
        RawHeader("Authorization", "Token " + token)
      )
    )

    responseFuture.flatMap(_.entity.toStrict(5 seconds).map(_.data.utf8String))
  }

  def get(url: String):Future[String] = {

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = url
      )
    )

    responseFuture.flatMap(_.entity.toStrict(5 seconds).map(_.data.utf8String))
  }
}
