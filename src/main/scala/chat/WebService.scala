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

import akka.actor._
import akka.http.scaladsl.Http
import akka.stream._
import akka.actor.ActorLogging
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure,Success}

trait WebServiceActor extends Actor with ActorLogging {
  implicit val system = context.system
  implicit val executionContext: ExecutionContext = context.dispatcher
  implicit val materializer = ActorMaterializer() //materialize actor to access stream
  private  var binding: scala.concurrent.Future[akka.http.scaladsl.Http.ServerBinding] = null

  def ServiceBind(
    bindRoute: akka.stream.scaladsl.Flow[akka.http.scaladsl.model.HttpRequest,akka.http.scaladsl.model.HttpResponse,Any],
    bindPort: Int
  ): Unit = {
    binding = Http().bindAndHandle(bindRoute,"0.0.0.0", bindPort)

    // the rest of the sample code will go here
    binding.onComplete {
      //binding success check
      case Success(binding) =>
        val localAddress = binding.localAddress
        log.info(s"Server is listening on ${localAddress.getAddress}:${localAddress.getPort}")

      case Failure(e) =>
        log.warning(s"Binding failed with ${e.getMessage}")
    }
  }

  def ServiceUnbind() = {
    if( binding != null )
    {
      binding
        .flatMap(_.unbind())
        .onComplete(_ =>
          log.info("listening port unbinding ... ")
        )
    }
  }
}
