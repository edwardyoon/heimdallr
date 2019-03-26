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
import akka.actor.SupervisorStrategy._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import org.json4s._
import org.json4s.{DefaultFormats, JValue}
import java.util.concurrent.TimeUnit
import EventConstants._
import akka.stream.ActorMaterializer

/**
  * Parent actor of multiple chatroom actors.
  * @param envType
  */
class ChatSupervisor(envType: String) extends Actor with ActorLogging {
  implicit val system = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = context.dispatcher

  override val supervisorStrategy =
    OneForOneStrategy(
      maxNrOfRetries = 10,
      withinTimeRange = 1 minute,
      loggingEnabled = true) {
      case x: Exception =>
        log.info("FIXME: ChatSupervisor => " + x.toString)
        Resume
      case t =>
        super.supervisorStrategy.decider.applyOrElse( t, (_:Any) => Escalate )
    }

  override def preStart(): Unit = {
    ChatRooms.httpClient = context.actorOf(Props(new HttpClient), "hc")
    log.info( "Heimdallr ChatSupervisor Staring ..." )
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.info( "Heimdallr ChatSupervisor Restarting ..." )
    log.info( reason.toString )
    preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info( "Heimdallr ChatSupervisor Restarted." )
    log.info( reason.toString )
  }

  override def postStop(): Unit = {
    log.info( "Heimdallr ChatSupervisor Down !" )
  }

  /**
    * @param number chatroom ID
    * @return the reference of chatRoomActor of given number
    */
  def getChatRoomActorRef(number:Int): ActorRef = {
    //create or get ChatRoom as an ActorRef
    this.synchronized {
      ChatRooms.chatRooms.getOrElse(number, createNewChatRoom(number))
    }
  }

  /**
    * Creates new chatroom actor and adds chatRooms map
    *
    * @param number chatroom ID
    * @return the reference of newly created chatRoomActor
    */
  def createNewChatRoom(number: Int): ActorRef = {
    var chatroom: ActorRef = null
    try {
      //creates new ChatRoomActor and returns as an ActorRef
      chatroom = context.actorOf(Props(new ChatRoomActor(number, envType)), s"${number}")
      ChatRooms.chatRooms += number -> chatroom
    }
    catch {
      case e: Exception =>
        log.info(s"FIXME: Create new chat room(${number}) => " + e)
        self ! CreateChatRoom(number)
    }

    chatroom
  }

  def removeChatRoom(chatRoomID: Int): Unit = {
    this.synchronized {
      ChatRooms.chatRooms.remove(chatRoomID)
    }
  }

  override def receive: Receive = {
    case CreateChatRoom(chatRoomID) =>
      getChatRoomActorRef(chatRoomID)

    case RemoveChatRoom(chatRoomID) =>
      removeChatRoom(chatRoomID)

    case RegChatUser(chatRoomID, userActor) =>
      userActor ! JoinRoom(getChatRoomActorRef(chatRoomID))

    case RegProps(props, name) =>
      context.actorOf(props, name)

    case HeimdallrError =>
      throw new ArithmeticException()

    case HeimdallrChatStatus =>
      log.info( "Heimdallr ChatSupervisor Running ..." )

    // *** supervisor ! "akka://heimdallr/user/{Valid ActorName}"
    case path: String =>
      log.debug(s"checking path => $path")
      context.actorSelection(path) ! Identify(path)

    case ActorIdentity(path, Some(ref)) =>
      log.debug(s"found actor $ref on $path")

    // *** supervisor ! "/user/{Invalid ActorName}"
    case ActorIdentity(path, None) =>
      log.debug(s"could not find an actor on $path")

    case Terminated(user) =>
      log.info("Receive Terminated Event of ChatRoomActor")

    case x =>
      log.warning("ChatSupervisor Unknown message : " + x)
  }
}