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

import akka.actor.{Actor, ActorRef}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object UserActor {
  case class Connected(outgoing: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
}

/**
  * The actor that is created for all users. This actor receives messages
  * from ChatRoomActor and transmits payloads to the clients over a
  * WebSocket connection.
  *
  * @param chatRoom chatRoomActor reference that I belongs to.
  */
class UserActor(chatRoom: ActorRef) extends Actor {
  implicit val executionContext: ExecutionContext = context.dispatcher
  import UserActor._

  def receive = {
    case Connected(outgoing) =>
      context.become(connected(outgoing))
  }

  def connected(outgoing: ActorRef): Receive = {
    chatRoom ! ChatRoomActor.Join

    {
      case IncomingMessage(text) =>
        chatRoom ! ChatRoomActor.ChatMessage(text)

      case ChatRoomActor.ChatMessage(text) =>
        outgoing ! OutgoingMessage(text)
    }
  }

}
