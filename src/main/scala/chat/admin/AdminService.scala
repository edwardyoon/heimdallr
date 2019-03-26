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
package chat.admin

import scala.concurrent.ExecutionContext
import akka.actor._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import chat.{HealthyService, WebService}

class AdminService(healthy: HealthyService)
                  (implicit system: ActorSystem, mat: ActorMaterializer, dispatcher: ExecutionContext)
  extends WebService
    with CommonApi {

  private var chatSuper: ActorRef = null
  private val servicePort = 8090
  private val serviceRoute= //<- adjustable depended on client url
    get {
      pathPrefix("health") {
        path("up") {
          healthy.start()
          httpRespJson( "200 OK" )
        } ~
          path("down") {
            healthy.stop()
            httpRespJson( "200 OK" )
          }
        path("view") {
          var result: String = ""
          if(chatSuper != null) {
            chatSuper ! "akka://heimdallr/user/*"
            chatSuper ! "akka://heimdallr/user/cs/*"
            result = "200 OK"
          }
          else {
            result = "ChatSupervisor ActorRef is NULL"
          }
          httpRespJson(result)
        }
      }
    }

  def httpRespJson(body: String) = {
    complete( HttpEntity(ContentTypes.`application/json`, body+"\r\n") )
  }

  def setChatSupervisorActorRef(actorRef: ActorRef) = {
    chatSuper = actorRef
  }

  def start(): Unit = {
    log.debug( "Admin Server staring ..." )
    serviceBind(this.getClass.getSimpleName, serviceRoute, servicePort)
  }

  def stop(): Unit = {
    serviceUnbind(this.getClass.getSimpleName)
  }
}