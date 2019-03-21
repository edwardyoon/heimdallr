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
import akka.pattern.gracefulStop
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import java.util.concurrent.TimeUnit
import EventConstants._
import RedisEventHandler._

import org.json4s._
import org.json4s.{DefaultFormats, JValue}
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import org.json4s.jackson.JsonMethods._

object ChatRoomActor {
  case object Join
  case object Leave
  case object Block
  case object Close
  case class RegUser(userID: Int, nickName: String)
  case class SpecialTargetResponse(message: String)
  case class ChatMessageToLocalUsers(message: String)
  case class ChatMessage(message: String)
}

/**
  * The actor that is created for each chat room. Various kinds of messages
  * that represent chat room-related events such as comment sending or delivery
  * termination are all routed to this actor.
  *
  * ChatRoomActor is in charge of
  * publishing comments to Redis or storing comments in Redis for comment
  * synchronization between servers (more details on the following section).
  *
  * It also passes to UserActor all messages destined to the clients.
  *
  * @param chatRoomID ChatRoom Unique Number
  */
class ChatRoomActor(chatRoomID: Int, envType: String) extends PubSubService {
  implicit val system = context.system
  implicit val executionContext: ExecutionContext = context.dispatcher

  val prefix = system.settings.config.getString("akka.environment.pubsub-channel.prefix")
  val postfix = system.settings.config.getString("akka.environment.pubsub-channel.postfix")
  val chatRoomName = setChatRoomName(envType, prefix, postfix)

  val recvTimeout = system.settings.config.getInt(s"akka.environment.${envType}.chatroom-receive-timeout")
  val redisIp = system.settings.config.getString(s"akka.environment.${envType}.redis-ip")
  val redisPort = system.settings.config.getInt(s"akka.environment.${envType}.redis-port")

  import ChatRoomActor._
  private var failover: Boolean = true
  private var users: Set[ActorRef] = Set.empty
  private var member: Int = 0
  private var guest: Int = 0

  context.setReceiveTimeout(Duration.create(recvTimeout, TimeUnit.SECONDS))

  def setChatRoomName(env: String, prefix: String, postfix: String): String = {
    env match {
      case "live" | "standby" | "development" => prefix + self.path.name + postfix
      case _ => prefix + "unknown." + self.path.name + postfix
    }
  }

  def ConvertMsg4User(msg: String): String = {
    msg.replaceAll("\\p{Cntrl}", "")
  }

  def broadcast(msg: String): Unit = {
    val data = ConvertMsg4User(msg)
    users.foreach(_ ! ChatRoomActor.ChatMessage(data))
  }

  def updateIncrRoomUser(isGuest: Boolean, firstJoin: Boolean, joinUser: ActorRef) = {
    if(firstJoin) {
      users += joinUser
      environment.aggregator ! UpdateChatCount(chatRoomID, users.size, -1, -1)

      // we also would like to remove the user when its actor is stopped
      context.watch(joinUser)
    }
    else {
      if(isGuest) {
        guest += 1
      }
      else {
        member+= 1
      }

      environment.aggregator ! UpdateChatCount(chatRoomID, users.size, member, guest)
    }
  }

  def updateDecrRoomUser(isGuest: Boolean, isJoin: Boolean, termUser: ActorRef) = {
    if(isJoin) {
      if(isGuest) {
        guest -= 1
      }
      else {
        member-= 1
      }
    }

    users -= termUser
    environment.aggregator ! UpdateChatCount(chatRoomID, users.size, member, guest)

    if(users.isEmpty) {
      destroyChatRoom()
    }
  }

  def destroyChatRoom() = {
    failover = false
    context.parent ! RemoveChatRoom(chatRoomID)
    self ! UnsubscribeMessage(chatRoomName)
  }

  override def preStart(): Unit = {
    log.info(s"[#$chatRoomID] actor has created. ${chatRoomName}" )
    Subscribe(self, chatRoomName, redisIp, redisPort)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.info(s"[ChatRoomActor#$chatRoomID] Restarting ... ${chatRoomName}")
    preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info(s"[ChatRoomActor#$chatRoomID] Restarted ... ${chatRoomName}")
  }

  override def postStop(): Unit = {
    log.info(s"[ChatRoomActor#$chatRoomID] Stopped ... ${chatRoomName}")
  }

  /**
    * Receives messages from Server, and synchronizes messages with others.
    * @return nothing
    */
  def receive = {
    case ReceiveTimeout =>
      log.info("I'm on idle status over 3 hours. Kill myself.")
      destroyChatRoom()

    case Block =>
      log.info(s"[#$chatRoomID] receive Block Event:" + chatRoomName)

    case Join =>
      log.info(s"[ChatRoomActor#$chatRoomID] receive Join Event:" + chatRoomName)
      updateIncrRoomUser(false,true, sender())

    case Leave =>
      log.info(s"[#$chatRoomID] receive Leave Event:" + chatRoomName)
      updateDecrRoomUser(false, false, sender()) //<- new

    case Close =>
      gracefulStop(self, Duration.create(5, TimeUnit.SECONDS))
      log.info(s"[ChatRoomActor#$chatRoomID] ChatRoomActor PoisonPill")

    case RegUser(userID, nickName) => // todo : userinfo
      log.info(s"[#$chatRoomID] receive Registry User Event:" + chatRoomName)
      updateIncrRoomUser(false,false,null) //<- new

    case TermChatUser(chatRoomID, is_guest, uID, nick) =>
      log.info(s"[#$chatRoomID] receive TermChatUser Event:" + chatRoomName)
      updateDecrRoomUser(is_guest, true, sender()) //<- new

    case msg: ChatMessageToLocalUsers =>
      broadcast(msg.message)

    case msg: ChatMessage =>  // publish message to all chatRoomActor that subscribes same chatRoomName
      log.info(s"messageLog ${msg.message}")
      Publish(chatRoomName, msg.message)

    case SubscribeMessage(pubsub) =>
      pubsubTask(self, pubsub)

    case UnsubscribeMessage(channel) =>
      Unsubscribe(self, channel)

    case Terminated(user) => // for UserActor
      log.info(s"[#$chatRoomID] receive Terminated Event:" + chatRoomName)
  }
}
