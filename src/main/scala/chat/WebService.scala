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

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure,Success}
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.{ ServerBinding }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import org.slf4j.LoggerFactory

trait WebService {
  val log = LoggerFactory.getLogger("total")
  private var binding: scala.concurrent.Future[ServerBinding] = null

  def serviceBind(serviceName: String, bindRoute: Flow[HttpRequest, HttpResponse, Any], bindPort: Int)
                 (implicit actorSystem: ActorSystem, materializer: Materializer): Unit = {
    binding = Http().bindAndHandle(bindRoute,"0.0.0.0", bindPort)

    // the rest of the sample code will go here
    binding.onComplete {
      //binding success check
      case Success(binding) =>
        val localAddress = binding.localAddress
        log.info(s"${serviceName} is listening on ${localAddress.getAddress}:${localAddress.getPort}")

      case Failure(e) =>
        log.error(s"${serviceName} Binding failed with ${e.getMessage}")
    }
  }

  def serviceUnbind(serviceName: String) = {
    if( binding != null )
    {
      binding
        .flatMap(_.unbind())
        .onComplete(_ =>
          log.info(s"${serviceName} listening port unbinding ... ")
        )
    }
    else
      log.info( s"${serviceName} Unbinding Failed !" )
  }
}