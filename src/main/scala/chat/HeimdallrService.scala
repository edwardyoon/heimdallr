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

import scala.concurrent.ExecutionContext
import akka.actor._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import chat.admin.AdminService

/**
  * The server that routes the messages incoming from outside to the destination actors.
  */
object Heimdallr extends App {
  override def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("heimdallr", ConfigFactory.load())
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = system.dispatcher

    val env = argsValidation(system, args)
    val hs  = new HealthyService
    val as  = new AdminService(hs)
    val ws  = new ChatService(env, as)

    hs.start()
    as.start()
    ws.start()
  }

  def argsValidation(system: ActorSystem, args: Array[String]) = {
    val env = args.length match {
      case 1 => args(0) match {
        case "live" => args(0)
        case "standby" => args(0)
        case "dev" => "development"
        case "development" => args(0)
        case _ => bye(system)
      }

      case _ => bye(system)
    }
    env
  }

  def bye(system: ActorSystem) = {
    println( "\nUsage : ")
    println( " - sbt \"run [live|standby|development or dev]\"\n")
    system.terminate()
    null
  }
}
