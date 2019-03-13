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
import com.redis.RedisClient
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import java.util.concurrent.TimeUnit
import EventConstants._
import ChatRoomActor._
import SubServiceActor._
import RedisEventHandler._

object RedisEventHandler {
  case class Subscribe(channels: Seq[String])
  case class Unsubscribe(channels: Seq[String])
  case class Publish(channels: String, data: String)
}

object SubServiceActor {
  def props(redisSubClient: RedisClient) =
    Props(new SubServiceActor(redisSubClient))

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

    def getChannel(channel: String): Set[ActorRef] = {
      subscriber.getOrElse(channel, null)
    }
  }
}

class SubServiceActor(redisSubClient: RedisClient) extends Actor with ActorLogging {

  implicit val system = context.system
  implicit val executionContext: ExecutionContext = context.dispatcher

  var state = SubscriptionState(subscriber = Map.empty, subscribed = Map.empty)

  override def receive: Receive = {
    // from other actor
    case Subscribe(channels) =>
      log.info(s"debug/sub chan : ${channels}")

      state = state.onSubscribe(sender, channels: _*)
      redisSubClient.subscribe(channels.head, channels.tail: _*) {
        msg: PubSubMessage =>
          // from com.redis.PubSub.Consumer thread
          log.info(s"debug/${msg}")
          self ! msg
      }

    case Unsubscribe(channels) =>
      val oldState = state
      state = state.onUnsubscribe(sender, channels: _*)
      val toUnsubscribe = channels.filter(
        c => oldState.numSubscriber(c) > 0 && state.numSubscriber(c) == 0
      )
      if (toUnsubscribe.nonEmpty) {
        redisSubClient.unsubscribe(toUnsubscribe.head, toUnsubscribe.tail: _*)
      }

    /*
    E(java.lang.Exception: ERR only (P)SUBSCRIBE / (P)UNSUBSCRIBE / PING / QUIT allowed in this context)
    case Publish(channels, data) =>
      log.info("publish ... " + channels)
      redisSubClient.publish(channels, data)
    */

    // from com.redis
    case S(channel, count) =>
      log.info("subscribed to {} / subscribing to {}", channel, count)

    case U(channel, count) =>
      log.info("unsubscribed to {} / subscribing to {}", channel, count)

    case M(channel, data) =>
      val chs = state.getChannel(channel)
      if(chs != null) {
        chs.foreach{ ch =>
          log.info(s"message has arrived from Channel[${ch}] => ${data}")
          ch ! ChatRoomActor.ChatMessageToLocalUsers(data) //<- broadcast to chatroom users
        }
      }
      else {
        log.info(s"No Channel...")
      }

    case E(exception) => // NOT handling com.redis.redisclient.E
      log.info(exception.toString())
  }

  override def preStart(): Unit = {
    log.info(s"SubService Actor has created.")
  }
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    preStart()
  }
  override def postRestart(reason: Throwable): Unit = {}
  override def postStop(): Unit = {
    redisSubClient.disconnect
  }
}

class PubServiceActor(redisPubClient: RedisClient) extends Actor with ActorLogging {
  def receive = {
    case Publish(channel, message) =>
      redisPubClient.publish(channel, message)
  }

  override def preStart(): Unit = {
    log.info(s"PubService Actor has created.")
  }
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    preStart()
  }
  override def postRestart(reason: Throwable): Unit = {}
  override def postStop(): Unit = {
    redisPubClient.disconnect
  }
}