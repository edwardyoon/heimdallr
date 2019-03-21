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
package chat.admin

import akka.actor.ActorRef
import akka.pattern.ask
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import org.json4s.jackson.Serialization.write
import org.slf4j.LoggerFactory
import chat.{ChatRoomActor, ChatRooms, environment}
import chat.EventConstants._
import chat.Aggregator._
import FailoverApi._

object FailoverApi {
  case class FoverReason(ts: String, node: String, reason: String)
}

trait FailoverApi extends StatApi {
  val flog = LoggerFactory.getLogger("failover")

  def getFailoverJsonMessage(chatRoomID: Int, protocol: String): String = {
    s"""
       |{
       |  "protocol":"$protocol",
       |  "host":"${environment.hostName}",
       |  "roomid":"$chatRoomID"
       |}
        """
      .stripMargin
      .replaceAll("\n| ", "")
  }

  /**
    * Heimdallr Failover Command
    *
    * @param ptype Protocol
    * @return rooms stats
    */
  def failover(chatSuper: ActorRef, ptype: String) = {
    var json: String = ""
    try {
      environment.setServiceMode("failover")

      val future = environment.aggregator ? AggregatorCollection
      val aggCollection = Await.result(future, 1 second).asInstanceOf[AggCollection]
      val totalUserCount = aggCollection.roomStats.values.sum
      val memberCount = aggCollection.memberCount.values.sum
      val guestCount = aggCollection.guestCount.values.sum

      if (totalUserCount > 0) {
        val protocol: String = ptype match {
          case "heimdallr" => ptype
          case _ => "unknown"
        }

        flog.info("System > Failover > RoomSize: " + ChatRooms.chatRooms.size )
        flog.info("System > Failover > RoomInfo: " + CountRoomsTerm("rooms", aggCollection.roomStats))

        ChatRooms.chatRooms.foreach{ case (k: Int, v: ActorRef) =>
          flog.info(s"${k} ChatRoom Failover")

          val failoverJson: String = getFailoverJsonMessage(k,protocol)
          flog.info(failoverJson)

          v ! ChatRoomActor.ChatMessageToLocalUsers(failoverJson)
          Thread.sleep(10)
        }
      }

      json = CountTotalOnly(totalUserCount, memberCount, guestCount)
    }
    catch {
      case e: Exception =>
        val exceptionMsg = s"failover exception: ${e}"
        json = FailoverRespReason(exceptionMsg)
        flog.info(exceptionMsg)
    }

    json
  }

  def FailoverRespReason(msg: String): String = {
    val hostName = environment.hostName
    write(
      FoverReason(
        ts    = getCurrentDateTime(),
        node  = hostName,
        reason= msg
      )
    )
  }
}

