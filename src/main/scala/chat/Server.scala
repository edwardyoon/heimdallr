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

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.io.StdIn
import com.typesafe.config.ConfigFactory
import scala.util.{Failure,Success}
import scala.concurrent.ExecutionContext.Implicits._

object Server {
  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("heimdallr", ConfigFactory.load())
    implicit val materializer = ActorMaterializer()

    var chatRooms: Map[Int, ActorRef] = Map.empty[Int, ActorRef]

    def newUser(chatRoomID: Int): Flow[Message, Message, NotUsed] = {
       // Gets chatroom actor reference
      val chatRoom = getChatRoomActorRef(chatRoomID)
      // new connection - new user actor
      val userActor = system.actorOf(Props(new UserActor(chatRoom)))

      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message].map {
          // transform websocket message to domain message
          case TextMessage.Strict(text) => UserActor.IncomingMessage(text)
          // PoisonPill asynchronously stops disconnected user actor
        }.to(Sink.actorRef[UserActor.IncomingMessage](userActor, PoisonPill))

      val outgoingMessages: Source[Message, NotUsed] =
        Source.actorRef[UserActor.OutgoingMessage](10000, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            // give the user actor a way to send messages out
            userActor ! UserActor.Connected(outActor)
            NotUsed
          }.map(
          // transform domain message to web socket message
          (outMsg: UserActor.OutgoingMessage) => TextMessage(outMsg.text))

      // then combine both to a flow
      Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
    }

    def getChatRoomActorRef(number:Int): ActorRef = {
      chatRooms.getOrElse (number, createNewChatRoom (number) )
    }

    def createNewChatRoom(number: Int): ActorRef = {
      val chatroom = system.actorOf(Props(new ChatRoomActor), "chat" + number)
      chatRooms += number -> chatroom
      chatroom
    }

    val route =
      pathPrefix(IntNumber) {
        chatRoomID => {
          handleWebSocketMessages(newUser(chatRoomID))
        }
      }

    val binding = Http().bindAndHandle(route, "0.0.0.0", 8080)

    // the rest of the sample code will go here
     binding.onComplete{
      case Success(binding) =>
        val localAddress = binding.localAddress
        println(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
      case Failure(e) =>
        println(s"Binding failed with ${e.getMessage}")
        system.terminate()
  }

}
