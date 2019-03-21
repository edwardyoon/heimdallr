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

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import chat.environment
import chat.admin.StatApi._
import chat.EventConstants._
import chat.Aggregator._

object StatApi {
  case class UserInfo(id: Int, nick: String)
  case class Data(protocol: String, host: String)
  case class HFailOver(event: String, data: Data)
  case class HFailOverMsg(data: HFailOver)
  case class StatsAll(ts: String, node: String, count: mutable.Map[Int, Int], rooms: mutable.Map[Int, ListBuffer[UserInfo]])
  case class StatsRooms(ts: String, node: String, rooms: mutable.Map[Int, ListBuffer[UserInfo]])
  case class StatsRoomUsers(ts: String, node: String, prop: String, users: ListBuffer[UserInfo])
  case class StatsCount(ts: String, node: String, count: mutable.Map[Int, Int])
  case class StatsTotal(ts: String, node: String, prop: String, total: Int, member: Int, guest: Int)
  case class StatsReason(ts: String, node: String, reason: String)
}

trait StatApi extends CommonApi {
  implicit val timeout = Timeout(3 seconds)
  implicit val formats = Serialization.formats(NoTypeHints)

  def CountTotalOnly(total: Int = 0, member: Int = 0, guest: Int = 0): String = {
    val hostName = environment.hostName
    var totalCount: Int = 0
    var memberCount: Int = 0
    var guestCount: Int = 0

    environment.aggregator ! AggregatorView

    if(total == 0 && member == 0 && guest == 0) {
      val future = environment.aggregator ? AggregatorCollection
      val aggCollection = Await.result(future, 1 second).asInstanceOf[AggCollection]
      totalCount = aggCollection.roomStats.values.sum
      memberCount = aggCollection.memberCount.values.sum
      guestCount = aggCollection.guestCount.values.sum
    }

    write(
      StatsTotal(
        ts    = getCurrentDateTime(),
        node  = hostName,
        prop  = "All",
        total = totalCount,
        member= memberCount,
        guest = guestCount
      )
    )
  }

  def CountRoomsTerm(term: String, roomStats: mutable.Map[Int, Int]): String = {
    write( StatsCount( ts = getCurrentDateTime(), node = environment.hostName, count = roomStats ) )
  }
}

