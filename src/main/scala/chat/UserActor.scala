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
import akka.actor._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import EventConstants._

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._


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
class UserActor(chatRoomID: Int, chatSuper: ActorRef) extends Actor with ActorLogging {
  implicit val executionContext: ExecutionContext = context.dispatcher
  context.setReceiveTimeout(Duration.create(12, TimeUnit.HOURS))

  import UserActor._
  private var joinRetry: Int = 0
  private var chatRoom: ActorRef = null


  override def preStart(): Unit = {
    chatRoom = ChatRooms.chatRooms.getOrElse(chatRoomID, null)
    if(chatRoom == null) {
      chatSuper ! RegChatUser(chatRoomID, self)
      log.info(s"[#$chatRoomID] Get a ChatRoomActorRef for UserActor" )
    }

    log.info(s"[#$chatRoomID] UserActor Staring ..." )
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.info(s"[#$chatRoomID] UserActor Restarting ..." )
    preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info(s"[#$chatRoomID] UserActor Restarted." )
  }

  override def postStop(): Unit = {
    var userVal: String = null

    if(chatRoom != null) {
      chatRoom ! ChatRoomActor.Leave
    }
    else {
      log.info(s"[#$chatRoomID] UserActor couldn't get chatroom!" )
    }

    log.info(s"[#$chatRoomID] UserActor($userVal) is gonna stop!" )
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
      if(chatRoom == null) {
        if(joinRetry < 1000) {
          joinRetry += 1
          self ! UserActor.Connected(outgoing) //<-Retry
        }
        else {
          log.debug(s"[#$chatRoomID] Become->State(Default): chatRoom is null ... Retry Limit over, UserActor PoisonPill")
          self ! PoisonPill
        }
      }
      else {
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
      log.debug(s"[#$chatRoomID] Become->State: User Blocked")

    case Left(outgoing) =>
      context.become(left(outgoing))
      log.debug(s"[#$chatRoomID] Become->State: User Left")

    case x => log.warning("User Actor Receive Default / Unknown Message : " + x)
  }

  def connected(outgoing: ActorRef): Receive = {
    if(chatRoom == null) {
      log.debug(s"[Debug] User Actor ChatRoom Actor is null")
    }
    chatRoom ! ChatRoomActor.Join
    log.debug(s"[#$chatRoomID] Join at ChatRoom($chatRoomID)")

    {
      // passes incoming text as a chat message to chatroom actor
      case IncomingMessage(text) =>
        if(chatRoom == null) {
          log.error("User Actor ... IncomingMessage ... ChatRoom Actor is NULL")
        }
        else {
          chatRoom ! ChatRoomActor.ChatMessage(text)
        }

      // delivers outgoing messasge to client
      case ChatRoomActor.ChatMessage(text) =>
        outgoing ! OutgoingMessage(text)

      case x => log.warning("User Actor Receive Connected / Unknown Message : " + x)
    }
  }

  def blocked(outgoing: ActorRef): Receive = {
    chatRoom ! ChatRoomActor.Block
    log.debug(s"[#$chatRoomID] Block at ChatRoom($chatRoomID)")

    {
      case _ =>
        chatRoom ! ChatRoomActor.ChatMessage(s"User Bocked.")
    }
  }

  def left(outgoing: ActorRef): Receive = {
    chatRoom ! ChatRoomActor.Leave
    log.info(s"[#$chatRoomID] Leave at ChatRoom($chatRoomID)")

    {
      case _ =>
        chatRoom ! ChatRoomActor.ChatMessage(s"User Left.")
    }
  }
}

