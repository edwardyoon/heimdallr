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
  implicit val system = ActorSystem("spoonchat", ConfigFactory.load())

  import ChatRoomActor._
  var users: Set[ActorRef] = Set.empty

  // TODO make this configurable
  val s = new RedisClient("localhost", 6379)
  val p = new RedisClient("localhost", 6379)

  s.subscribe("chat") { pubsub =>
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

          // if message is coming from others, broadcast to locally connected users
          case x =>
            println("received message on channel " + channel + " as : " + x)
            users.foreach(_ ! ChatRoomActor.ChatMessage(x))
        }
    }
  }

  def receive = {
    case Join =>
      users += sender()
      // we also would like to remove the user when its actor is stopped
      context.watch(sender())

    case Terminated(user) =>
      users -= user

    case msg: ChatMessage =>
      // sync local message with others
      p.publish("chat", msg.message);
      users.foreach(_ ! msg)
  }

}
