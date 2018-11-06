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

import akka.actor.{ActorRef, ActorSystem, Props}

/**
  * This object manages a list of locally created chat rooms
  */
object ChatRooms {

  var chatRooms: Map[Int, ActorRef] = Map.empty[Int, ActorRef] //existing rooms

  /**
    * @param number chatroom ID
    * @return the reference of chatRoomActor of given number
    */
  def getChatRoomActorRef(system: ActorSystem, number:Int): ActorRef = {
    //create or get ChatRoom as an ActorRef
    this.synchronized {
      chatRooms.getOrElse(number, createNewChatRoom(system, number))
    }
  }

  /**
    * Creates new chatroom actor and adds chatRooms map
    *
    * @param number chatroom ID
    * @return the reference of newly created chatRoomActor
    */
  def createNewChatRoom(system: ActorSystem, number: Int): ActorRef = {
    //creates new ChatRoomActor and returns as an ActorRef
    val chatroom = system.actorOf(Props(new ChatRoomActor), "chat" + number)
    chatRooms += number -> chatroom
    chatroom
  }

}
