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
import com.redis.{E, M, PubSubMessage, RedisClient, S, U}
import RedisEventHandler._
import chat.ChatRoomActor.Close

object RedisEventHandler {
  case class SubscribeMessage(pubsub: PubSubMessage)
  case class UnsubscribeMessage(channel: String)
}

trait PubSubService extends Actor with ActorLogging {
  private var redisPubClient: RedisClient = null
  private var redisSubClient: RedisClient = null

  def Subscribe(chatRoomActor: ActorRef, channel: String, ip: String, port: Int) = {
    redisPubClient = new RedisClient(ip, port)
    redisSubClient = new RedisClient(ip, port)
    redisSubClient.subscribe(channel) {
      msg: PubSubMessage => // from com.redis.PubSub.Consumer thread
        chatRoomActor ! SubscribeMessage(msg)
    }
  }

  def Unsubscribe(chatRoomActor: ActorRef, channel: String) = {
    redisSubClient.unsubscribe(channel)
  }

  def Publish(channel: String, message: String) = {
    redisPubClient.publish(channel, message)
  }

  def pubsubTask(chatRoomActor: ActorRef, pubsub: PubSubMessage) = pubsub match {
    case S(channel, count) =>
      log.info(s"${channel} PubSubService subscribed / subscribing to ${count}")

    case U(channel, count) =>
      disconnection(chatRoomActor)
      log.info(s"${channel} PubSubService Unsubscribed(remain:${count}) & Disconnected.")

    case M(channel, data) =>
      chatRoomActor ! ChatRoomActor.ChatMessageToLocalUsers(data) //<- broadcast to chatroom users

    case E(exception) => // NOT handling com.redis.redisclient.E
      log.info("Subscribe Exception: " + exception.toString())
  }

  def disconnection(chatRoomActor: ActorRef) = {
    redisPubClient.disconnect
    redisSubClient.disconnect
    chatRoomActor ! ChatRoomActor.Close
  }
}
