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

import java.net._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor._
import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.RemoteAddress
import chat.admin.AdminService
import EventConstants._
//import chat.utils.HttpClient

class ChatService(env: String, admin: AdminService)
                 (implicit system: ActorSystem, mat: ActorMaterializer, dispatcher: ExecutionContext)
  extends WebService {

  private var chatSuper:  ActorRef  = null
  private var aggregator: ActorRef  = null
  private var httpClient: HttpClient= null

  private val servicePort = 8000
  private val serviceRoute= //<- adjustable depended on client url
    pathPrefix(IntNumber) {
      chatRoomID => {
        chatSuper ! CreateChatRoom(chatRoomID)
        extractClientIP {
          ip =>
            handleWebSocketMessages(newUser(chatRoomID, ip))
        }
      }
    }

  def regNode(port: Int) = {
    val localhost = InetAddress.getLocalHost
    val localIpAddress = localhost.getHostAddress

    environment.hostName = localIpAddress
    environment.port = port

    log.info(s"Server IP Address of System => ${localIpAddress}")
  }

  def incomingMessages(userActor: ActorRef): Sink[Message, NotUsed] = {
    Flow[Message].map {
      // transform websocket message to domain message
      case TextMessage.Strict(text) => UserActor.IncomingMessage(text)

      // PoisonPill asynchronously stops disconnected user actor
      //TODO : to deal with join, leave, text message types
    }.to(Sink.actorRef[UserActor.IncomingMessage](userActor, PoisonPill))
  }

  def outgoingMessages(userActor: ActorRef): Source[Message, NotUsed] = {
    Source.actorRef[UserActor.OutgoingMessage](20000, OverflowStrategy.fail)
      .mapMaterializedValue { outActor =>
        // give the user actor a way to send messages out
        userActor ! UserActor.Connected(outActor)
        NotUsed
      }.map(
      // transform domain message to web socket message
      (outMsg: UserActor.OutgoingMessage) => TextMessage(outMsg.text))
  }

  def newUser(chatRoomID: Int, ip: RemoteAddress): Flow[Message, Message, NotUsed] = {
    // new connection - new user actor
    val userActor = system.actorOf(Props(new UserActor(chatRoomID, chatSuper, ChatRooms.httpClient, ip)))

    // Set Sink & Source
    val incomingMsg = incomingMessages(userActor)
    val outgoingMsg = outgoingMessages(userActor)

    Flow.fromSinkAndSource(incomingMsg, outgoingMsg)
  }

  def start() {
    log.info( "ChatService staring ..." )

    environment.setEnvType(env)
    environment.version = system.settings.config.getString("akka.heimdallr-version" )

    chatSuper = system.actorOf(Props(classOf[ChatSupervisor], env), "cs")
    admin.setChatSupervisorActorRef(chatSuper)

    regNode(servicePort)
    serviceBind(this.getClass.getSimpleName, serviceRoute, servicePort)

    log.info(environment.HeimdallrLogo(system))
  }

  def stop(): Unit = {
    serviceUnbind(this.getClass.getSimpleName)
  }
}