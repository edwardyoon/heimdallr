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
import com.typesafe.config.ConfigFactory
import EventConstants._

/**
  * The server that routes the messages incoming from outside to the destination actors.
  */
object Heimdallr extends App {
  override def main(args: Array[String]): Unit = {
    implicit val heimdallrSystem = ActorSystem("heimdallr", ConfigFactory.load())
    val supervisor = heimdallrSystem.actorOf(Props(new RouteSupervisor(heimdallrSystem)), "Supervisor")

    supervisor ! HeimdallrStart(args)

  /** for Debug 
    *
    Thread.sleep(1000)
    supervisor ! HeimdallrView
    Thread.sleep(1000)

    heimdallrSystem.actorSelection("akka://heimdallr/user/Supervisor/ws") ! HeimdallrError
    heimdallrSystem.actorSelection("akka://heimdallr/user/Supervisor/cs") ! HeimdallrChatStatus
  */
  }
}

