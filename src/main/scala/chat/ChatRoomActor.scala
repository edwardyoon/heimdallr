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

import com.redis.{RedisClient, PubSubMessage, S, U, E, M}
import scala.concurrent.ExecutionContext
import com.redis.RedisClient
import com.typesafe.config.ConfigFactory

object ChatRoomActor {
  case object Join
  case class ChatMessage(message: String)
}

class ChatRoomActor extends Actor {
  implicit val executionContext: ExecutionContext = context.dispatcher
  implicit val system = ActorSystem("heimdallr", ConfigFactory.load())

  import ChatRoomActor._
  var users: Set[ActorRef] = Set.empty

  val chatRoomName = self.path.name
  var redisIp = system.settings.config.getString("akka.redis-ip")
  var redisPort = system.settings.config.getInt("akka.redis-port")
  val s = new RedisClient(redisIp, redisPort)
  val p = new RedisClient(redisIp, redisPort)

  s.subscribe(chatRoomName) { pubsub =>
    pubsub match {
      case S(channel, no) => println("subscribed to " + channel + " and count = " + no)
      case U(channel, no) => println("unsubscribed from " + channel + " and count = " + no)
      case E(exception) => println(exception + "Fatal error caused consumer dead. " +
        "Need to reconnecting to master or connect to backup")

      case M(channel, msg) =>
        msg match {
          // exit will unsubscribe from all channels and stop subscription service
          case "exit" =>
            println("unsubscribe all ..")
            s.unsubscribe

          case x if x startsWith "-" =>
            val p : Seq[Char] = x
            p match {
              case Seq('-', rest @ _*)=>
                s.unsubscribe(rest.toString)
            }

          case x if x startsWith "+" =>
            val p : Seq[Char] = x
            p match {
              case Seq('+', rest @ _*)=>
                s.subscribe(rest.toString){m => }
            }
            
          // Broadcasts to locally connected users
          case x =>
            println("received message on channel " + channel + " as : " + x)
            users.foreach(_ ! ChatRoomActor.ChatMessage(x))
        }
    }
  }

  /**
    * Receives messages from Server, and processes messages.
    * 
    * @return
    */
  def receive = {
    case Join =>
      users += sender()
      // we also would like to remove the user when its actor is stopped
      context.watch(sender())

    case Terminated(user) =>
      users -= user

    case msg: ChatMessage =>
      // publish message to all chatRoomActor that subscribes same chatRoomName
      p.publish(chatRoomName, msg.message);
  }
}
