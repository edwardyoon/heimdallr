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

import scala.concurrent.ExecutionContext
import com.redis.RedisClient

object ChatRoomActor {
  case object Join
  case class ChatMessage(message: String)
}

class ChatRoomActor extends Actor {
  implicit val executionContext: ExecutionContext = context.dispatcher

  import ChatRoomActor._
  var users: Set[ActorRef] = Set.empty

  val r = new RedisClient("localhost", 6379)
  r.set("key1", "abc")

  println("subscribe channel: chat")

  def receive = {
    case Join =>
      users += sender()
      // we also would like to remove the user when its actor is stopped
      context.watch(sender())

    case Terminated(user) =>
      users -= user

    case msg: ChatMessage =>
      users.foreach(_ ! msg)
  }
}
