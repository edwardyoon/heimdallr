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

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import org.json4s._
import org.json4s.{DefaultFormats, JValue}
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import org.json4s.jackson.JsonMethods._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import chat.EventConstants.{AggregatorCollectionToStats, AggregatorRoomValueToStats, AggregatorValueToStats, AggregatorView}
import org.slf4j.LoggerFactory

/**
  * This object manages multiple chat rooms
  */
object ChatRooms {
  implicit val timeout = Timeout(3 seconds)
  implicit val formats = Serialization.formats(NoTypeHints)

  // Map that contains existing chatRoom actor reference with ID
  var chatRooms: mutable.Map[Int, ActorRef] = mutable.Map.empty[Int, ActorRef]

  var roomUsers: mutable.Map[Int, ListBuffer[UserInfo]] = mutable.Map.empty[Int, ListBuffer[UserInfo]] //<- member
  var roomStats: mutable.Map[Int, Int] = mutable.Map.empty[Int, Int]  //<- guest + member
  var guestCount: mutable.Map[Int, Int] = mutable.Map.empty[Int, Int]
  var memberCount: mutable.Map[Int, Int] = mutable.Map.empty[Int, Int]

  var roomTotalCount: Int = 0
  var memberTotalCount: Int = 0
  var guestTotalCount: Int = 0
  var onceRoomTotalCount: Int = 0
  var onceMemberTotalCount: Int = 0
  var onceGuestTotalCount: Int = 0

  // Node Information
  var hostName: String = null
  var port: Int = 0

  // periodically update the block terms.
  var terms = List[String]()

  case class UserInfo(id: Int, nick: String)
  case class Data(protocol: String, host: String)
  case class StatsAll(ts: String, node: String, count: mutable.Map[Int, Int], rooms: mutable.Map[Int, ListBuffer[UserInfo]])
  case class StatsRooms(ts: String, node: String, rooms: mutable.Map[Int, ListBuffer[UserInfo]])
  case class StatsRoomUsers(ts: String, node: String, prop: String, users: ListBuffer[UserInfo])
  case class StatsCount(ts: String, node: String, count: mutable.Map[Int, Int])
  case class StatsTotal(ts: String, node: String, prop: String, total: Int, member: Int, guest: Int)
  case class StatsReason(ts: String, node: String, reason: String)

  def getCurrentDateTime(): String = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm").format(LocalDateTime.now)
  }

  def getCurrentDateTimeSec(): String = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss").format(LocalDateTime.now)
  }

  def RoomMemberInfor(chatRoomID: Int): String = {
    /* TODO ...
    var users = roomUsers.getOrElse(chatRoomID, null)
    if( users == null )
      RespReason( s"R#${chatRoomID} does not exgist !" )
    else write(
      StatsRoomUsers(
        ts    = getCurrentDateTime(),
        node  = hostName,
        prop  = chatRoomID.toString,
        users = roomUsers.getOrElse(chatRoomID, null)
      )
    )
    */
    RespReason( s"TODO ..." )
  }

  def collectionEmpty = {
    roomStats = mutable.Map.empty[Int, Int]  //<- guest + member
    guestCount = mutable.Map.empty[Int, Int]
    memberCount = mutable.Map.empty[Int, Int]
  }

  def CountRoomsTerm(term: String): String = {
    var json: String = null
    val future = environment.aggregator ? AggregatorCollectionToStats
    Await.result(future, 3 seconds)

    term match {
      case "all" =>
        json = write( StatsAll( ts = getCurrentDateTimeSec(), node = hostName, count = roomStats, rooms = roomUsers ) )
      case "users" =>
        json = write( StatsRooms( ts = getCurrentDateTimeSec(), node = hostName, rooms = roomUsers ) )
      case "rooms" =>
        json = write( StatsCount( ts = getCurrentDateTime(), node = hostName, count = roomStats ) )
      case "member" =>
        json = write( StatsCount( ts = getCurrentDateTime(), node = hostName, count = memberCount ) )
      case "guest" =>
        json = write( StatsCount( ts = getCurrentDateTime(), node = hostName, count = guestCount ) )
    }

    collectionEmpty
    json
  }

  def CountTotalOnly(): String = {
    environment.aggregator ! AggregatorView
    val future = environment.aggregator ? AggregatorValueToStats
    Await.result(future, 3 seconds)

    write(
      StatsTotal(
        ts    = getCurrentDateTime(),
        node  = hostName,
        prop  = "All",
        total = roomTotalCount,
        member= memberTotalCount,
        guest = guestTotalCount
      )
    )
  }

  def CountRoomOnce(chatRoomID: Int): String = {
    val future = environment.aggregator ? AggregatorRoomValueToStats(chatRoomID)
    Await.result(future, 1 seconds)

    if( onceRoomTotalCount == 0 )
      RespReason( s"R#${chatRoomID} does not exgist !" )
    else write(
        StatsTotal(
          ts    = getCurrentDateTime(),
          node  = hostName,
          prop  = chatRoomID.toString,
          total = onceRoomTotalCount,
          member= onceMemberTotalCount,
          guest = onceGuestTotalCount
        )
    )
  }

  def RespReason(msg: String): String = {
    write(
      StatsReason(
        ts    = getCurrentDateTime(),
        node  = hostName,
        reason= msg
      )
    )
  }

  def ConvertMsg4User(msg: String): String = {
    write((parse(msg.replaceAll("\\p{Cntrl}", ""))(1) \ "data")(1))
  }

  def HttpRespJson(body: String) = {
    complete( HttpEntity(ContentTypes.`application/json`, body+"\r\n") )
  }

  def removeChatRoom(chatRoomID: Int): Unit = {
    this.synchronized {
      chatRooms.remove(chatRoomID)
    }
  }

  def setTotalCountValues(total:Int, member:Int, guest:Int) = {
    roomTotalCount = total
    memberTotalCount = member
    guestTotalCount = guest
    roomTotalCount
  }

  def setOnceTotalCountValues(total:Int, member:Int, guest:Int) = {
    onceRoomTotalCount = total
    onceMemberTotalCount = member
    onceGuestTotalCount = guest
    onceRoomTotalCount
  }

  def setCollectionValues(room: mutable.Map[Int,Int], member: mutable.Map[Int,Int], guest: mutable.Map[Int,Int]) = {
    roomStats = room
    guestCount = guest
    memberCount = member
    true
  }
}

