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
//import com.redis._
//import com.redis.{E, M, PubSubMessage, RedisClient, S, U}
import com.redis.{E, M, PubSubMessage, RedisClient, S, U}
import com.redis.RedisClient
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import chat.ChatRooms.UserInfo
import EventConstants._
import ChatRoomActor._

object RedisEventHandler {
  case class Subscribe(channels: Seq[String])
  case class Unsubscribe(channels: Seq[String])
  case class Publish(channels:Seq[String], data: String)
}

object PubSubServiceActor {
  def props(redisClient: RedisClient) =
    Props(new PubSubServiceActor(redisClient))
  //Props(new RedisClientActor(redisClient))

  case class SubscriptionState(subscriber: Map[String, Set[ActorRef]],
                               subscribed: Map[ActorRef, Set[String]]) {
    def onSubscribe(actor: ActorRef, channels: String*): SubscriptionState = {

      val newSubscribed = subscribed.updated(
        actor,
        subscribed.getOrElse(actor, Set.empty) ++ channels
      )
      val newSubscriber = channels.foldLeft(subscriber) { (s, channel) =>
        s.updated(channel, s.getOrElse(channel, Set.empty) + actor)
      }

      SubscriptionState(subscriber = newSubscriber, subscribed = newSubscribed)
    }

    def onUnsubscribe(actor: ActorRef, channels: String*): SubscriptionState = {

      val newSubscribed = subscribed.updated(
        actor,
        subscribed.getOrElse(actor, Set.empty) -- channels
      )
      val newSubscriber = channels.foldLeft(subscriber) { (s, channel) =>
        s.updated(channel, s.getOrElse(channel, Set.empty) - actor)
      }
      SubscriptionState(subscriber = newSubscriber, subscribed = newSubscribed)
    }

    def numSubscriber(channel: String): Int = {
      subscriber.getOrElse(channel, Set.empty).size
    }

    def getChannel(channel: String): ActorRef = {
      subscriber.getOrElse(channel, null)
    }
  }
}

//class ChatRoomActor(chatRoomID: Int, envType: String) extends Actor with ActorLogging {
//class RedisClientActor(chatRoomID: Int, envType: String) extends Actor with ActorLogging {
//class PubSubServiceActor(envType: String) extends Actor with ActorLogging {
class PubSubServiceActor(redisClient: RedisClient) extends Actor with ActorLogging {
  import RedisEventHandler._

  implicit val system = context.system
  implicit val executionContext: ExecutionContext = context.dispatcher

  /*
  val prefix      = system.settings.config.getString("akka.environment.pubsub-channel.prefix")
  val postfix     = system.settings.config.getString("akka.environment.pubsub-channel.postfix")
  //val chatRoomName = setChatRoomName(envType, prefix, postfix)

  val recvTimeout = system.settings.config.getInt(s"akka.environment.${envType}.chatroom-receive-timeout")
  val redisIp     = system.settings.config.getString(s"akka.environment.${envType}.redis-ip")
  val redisPort   = system.settings.config.getInt(s"akka.environment.${envType}.redis-port")
  val redisClient = new RedisClient(redisIp, redisPort)
  //var p = new RedisClient(redisIp, redisPort)
  */

  var state = SubscriptionState(subscriber = Map.empty, subscribed = Map.empty)

  override def receive: Receive = {
    // from other actor
    case Subscribe(channels) =>
      state = state.onSubscribe(sender, channels: _*)
      redisClient.subscribe(channels.head, channels.tail: _*) {
        msg: PubSubMessage =>
          // from com.redis.PubSub.Consumer thread
          self ! msg
      }

    case Unsubscribe(channels) =>
      val oldState = state
      state = state.onUnsubscribe(sender, channels: _*)
      val toUnsubscribe = channels.filter(
        c => oldState.numSubscriber(c) > 0 && state.numSubscriber(c) == 0
      )
      if (toUnsubscribe.nonEmpty) {
        redisClient.unsubscribe(toUnsubscribe.head, toUnsubscribe.tail: _*)
      }

    case Publish(channels, data) =>
      redisClient.publish(channels, data)

    // from com.redis
    case S(channel, count) =>
      log.info("subscribed to {} / subscribing to {}", channel, count)

    case U(channel, count) =>
      log.info("unsubscribed to {} / subscribing to {}", channel, count)

    case M(channel, data) =>
      log.info("message has arrived from redis.")
      getChannel(channel) ! ChatRoomActor.ChatMessageToLocalUsers(data)
    //val ch = getChannel(channel)
    //ch ! ChatRoomActor.ChatMessageToLocalUsers(failoverJson)

    case E(exception) => // NOT handling com.redis.redisclient.E
      log.info(exception.toString())
  }

  /*
  def broadcast(message: String): Unit = {
    users.foreach(_ ! ChatRoomActor.ChatMessage(message))
  }

  def connectToRedis(): Unit = {
    if(failover)
    {
      try {
        if(!s.connected) {
          s = new RedisClient(redisIp, redisPort)
        }
        if(!p.connected)
          p = new RedisClient(redisIp, redisPort)

        subscribe()
      } catch {
        case x: Exception =>
          log.info("Retry to connect redis. It caused by " + x)
          connectToRedis()
      } finally {
        log.info("## Redis connection has established.")
      }
    }
    else {
      log.info(s"[#$chatRoomID] Became PoisonPill, Retry to Disconnect redis ... ")
      if(p.connected && s.connected)
      {
        //s.unsubscribe(chatRoomName)
        p.disconnect
        s.disconnect
        log.info(s"[#$chatRoomID] Retry, Redis Disconnected.")
      }
      else
        log.info(s"[#$chatRoomID]  => Redis Disconnected.")
    }
  }

  def subscribe(): Unit = {
    s.subscribe(chatRoomName) { pubsub =>
      pubsub match {
        case S(channel, no) => log.info("subscribed to " + channel + " and count = " + no)
        case U(channel, no) => log.info("unsubscribed from " + channel + " and count = " + no)
        case E(exception) =>
          p.disconnect
          s.disconnect
          if (exception.toString.equals("com.redis.RedisConnectionException: Connection dropped ..")) {
            log.error(exception + ", #1 Fatal error caught at Redis subscribe(). :" + chatRoomName)
            connectToRedis()
          } else {
            log.error(exception + ", #2 Fatal error caught at Redis subscribe(). :" + chatRoomName)
          }

        case M(channel, msg) =>
          broadcast(msg)
      }
    }
  }

  */
}
