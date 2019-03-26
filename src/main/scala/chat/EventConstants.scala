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

object EventConstants {

  case object HeimdallrStop
  case object HeimdallrError
  case object HeimdallrView
  case object HeimdallrChatStatus
  case object HeimdallrFailover

  case object WebServiceStart
  case object WebServiceStop

  case object HealthUp
  case object HealthDown

  case class HeimdallrStart(args: Array[String])
  case class HeimdallrActorDown(actorRef: ActorRef)

  case class CreateChatRoom(roomId: Int)
  case class RegNodeInfor(hostName: String, port: Int)
  case class RegChatUser(roomId: Int, userActor: ActorRef)
  case class TermChatUser(roomId: Int, is_guest: Boolean, uid: Int, nick: String)
  case class JoinRoom(chatRoomActorRef: ActorRef)
  case class RemoveChatRoom(chatRoomID: Int)
  case class UpdateChatCount(chatRoomID: Int, users: Int, member: Int, guest: Int)

  case class RegActor(actorRef: ActorRef)
  case class RegProps(props: Props, name: String)
  case class StopActor(actorRef: ActorRef)
}
