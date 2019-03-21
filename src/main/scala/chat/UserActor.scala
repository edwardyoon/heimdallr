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

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure, Success}
import akka.actor._
import akka.pattern.{ask,pipe}
import akka.util.Timeout
import akka.http.scaladsl.model.RemoteAddress
import EventConstants._

object UserActor {
  case class Connected(outgoing: ActorRef)
  case class Blocked(outgoing: ActorRef)
  case class Left(outgoing: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
}

/**
  * The actor that is created for all users. This actor receives messages
  * from ChatRoomActor and transmits payloads to the clients over a
  * WebSocket connection.
  *
  * @param chatRoomID ChatRoom Unique Number
  */
class UserActor(chatRoomID: Int, chatSuper: ActorRef, ip: RemoteAddress) extends Actor with ActorLogging {
  import UserActor._
  implicit val executionContext: ExecutionContext = context.dispatcher
  context.setReceiveTimeout(Duration.create(2, TimeUnit.HOURS))

  private var joinRetries: Int = 0
  private var chatRoom: ActorRef = null

  override def preStart(): Unit = {
    this.synchronized {
      chatRoom = ChatRooms.chatRooms.getOrElse(chatRoomID, null)
    }
    if (chatRoom == null) {
      chatSuper ! RegChatUser(chatRoomID, self)
      log.info(s"[#$chatRoomID] gets a ChatRoomActorRef for UserActor" )
    }
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info(s"[#$chatRoomID] user actor has restarted." )
  }

  override def postStop(): Unit = {
    var userVal: String = null

    if (chatRoom != null) {
      // TODO : Process Member & Guest
      chatRoom ! ChatRoomActor.Leave
    } else {
      log.info(s"[#$chatRoomID] UserActor couldn't get chatroom!" )
    }

    log.info(s"[#$chatRoomID] UserActor($userVal) is stopped!" )
  }

  // Initializes the defaults for session creation.
  def receive = {
    case ReceiveTimeout =>
      log.info("I'm on idle status over 3 hours. Kill myself.")
      context.stop(self)

    case JoinRoom(chatRoomActorRef) =>
      chatRoom = chatRoomActorRef
      log.info(s"[#$chatRoomID] Set ChatRoom : $chatRoomID")

    case Connected(outgoing) =>
      if (chatRoom == null) {
        if (joinRetries < 1000) {
          joinRetries += 1
          self ! UserActor.Connected(outgoing) //<-Retry
        } else {
          log.debug(s"[#$chatRoomID] It looks like chatRoom is null ... just drinking poison myself.")
          self ! PoisonPill
        }
      } else {
        context.become(connected(outgoing))
        log.debug(s"[#$chatRoomID] Become->State: User Connected")
      }

    case IncomingMessage(text) =>
      context.system.scheduler.scheduleOnce(Duration.create(1, TimeUnit.MILLISECONDS)) {
        log.debug(s"[#$chatRoomID] Become->State(Default): Received Incoming Message ... Retry in 1ms")
        self ! IncomingMessage(text)
      }

    case Blocked(outgoing) =>
      context.become(blocked(outgoing))
      log.debug(s"[#$chatRoomID] Become->State: User has blocked")

    case Left(outgoing) =>
      context.become(left(outgoing))
      log.debug(s"[#$chatRoomID] Become->State: User is just left")

    case x => log.warning("User Actor has received Unknown Message : " + x)
  }

  def connected(outgoing: ActorRef): Receive = {
    if (chatRoom == null) {
      log.debug(s"[Debug] User Actor ChatRoom Actor is null")
    }
    chatRoom ! ChatRoomActor.Join
    log.debug(s"[#$chatRoomID] Join at ChatRoom($chatRoomID)")

    {
      case ReceiveTimeout =>
        log.info("I'm on idle status over 3 hours. Kill myself.")
        context.stop(self)

      // passes incoming text as a chat message to chatroom actor
      case IncomingMessage(text) =>
        if (chatRoom == null) {
          log.error("User Actor ... IncomingMessage ... ChatRoom Actor is NULL")
        } else {
          chatRoom ! ChatRoomActor.ChatMessage(text)
        }

      // delivers outgoing messasge to client
      case ChatRoomActor.ChatMessage(text) =>
        outgoing ! OutgoingMessage(text)

      case x => log.warning("User Actor has received Unknown Message : " + x)
    }
  }

  def blocked(outgoing: ActorRef): Receive = {
    chatRoom ! ChatRoomActor.Block
    log.debug(s"[#$chatRoomID] Block at ChatRoom($chatRoomID)")

    {
      case _ =>
        chatRoom ! ChatRoomActor.ChatMessage(s"User has blocked.")
    }
  }

  def left(outgoing: ActorRef): Receive = {
    chatRoom ! ChatRoomActor.Leave
    log.info(s"[#$chatRoomID] leaves from ChatRoom($chatRoomID)")

    {
      case _ =>
        chatRoom ! ChatRoomActor.ChatMessage(s"User has left.")
    }
  }
}

