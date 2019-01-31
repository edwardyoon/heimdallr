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
import akka.http.scaladsl.server.Directives._
import EventConstants._

class AdminService extends WebServiceActor {
  val servicePort = 8090
  val serviceRoute= //<- adjustable depended on client url
    get {
      pathPrefix("health") {
        path("up") {
          context.parent ! HealthUp
          ChatRooms.HttpRespJson( "200 OK" )
        } ~
        path("down") {
          context.parent ! HealthDown
          ChatRooms.HttpRespJson( "200 OK" )
        } ~
        path("view") {
          context.parent ! HeimdallrView
          ChatRooms.HttpRespJson( "200 OK" )
        }
      } ~
      pathPrefix("stats") {
        path("all") {
          ChatRooms.HttpRespJson( ChatRooms.countRoomsTerm("all") )
        } ~
        path("rooms") {
          ChatRooms.HttpRespJson( ChatRooms.countRoomsTerm("users") )
        } ~
        pathPrefix("room") {
          path(IntNumber) {
            chatRoomID => {
              ChatRooms.HttpRespJson( ChatRooms.roomMemberInfor(chatRoomID) )
            }
          }
        } ~
        pathPrefix("count") {
          path("total") {
            ChatRooms.HttpRespJson( ChatRooms.CountTotalOnly() )
          } ~
          path("rooms") {
            ChatRooms.HttpRespJson( ChatRooms.countRoomsTerm("rooms") )
          } ~
          pathPrefix("rooms") {
            path("member") {
              ChatRooms.HttpRespJson( ChatRooms.countRoomsTerm("member") )
            } ~
            path("guest") {
              ChatRooms.HttpRespJson( ChatRooms.countRoomsTerm("guest") )
            }
          } ~
          pathPrefix("room") {
            path(IntNumber) {
              chatRoomID => {
                ChatRooms.HttpRespJson( ChatRooms.CountRoomOnce(chatRoomID) )
              }
            }
          }
        }
      } ~
      path("pretty" ) {
        ChatRooms.HttpRespJson( ChatRooms.countRoomsTerm("all") )
      }
    }

  override def preStart(): Unit = {
    log.debug( "Admin Server Staring ..." )
    serviceBind(serviceRoute, servicePort)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.debug( "Admin Server Restarting ..." )
    preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    log.debug( "Admin Server Restarted." )
  }

  override def postStop(): Unit = {
    serviceUnbind()
    log.debug( "Admin Server Down !" )
  }

  override def receive: Receive = {
    case WebServiceStart =>
      serviceBind(serviceRoute, servicePort)
    case WebServiceStop =>
      serviceUnbind()
    case x =>
      log.warning("AdminService Unknown message : " + x)
  }
}
