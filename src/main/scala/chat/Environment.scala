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

import akka.actor.ActorSystem

object environment {
  var version = "orange"
  var envType = "dev"
  var serviceMode: String = "running"
  var hostName: String = "localhost"
  var port: Int = 8000

  def setEnvType(str: String) = {
    envType = str
  }

  def getEnvType() = {
    envType
  }

  def setServiceMode(mode: String) = {
    serviceMode = mode
  }

  def getServiceMode(): String = {
    serviceMode
  }

  def HeimdallrLogo(system: ActorSystem) = {
    val redisIP  = system.settings.config.getString(s"akka.environment.${envType}.redis-ip")
    val redisPort= system.settings.config.getString(s"akka.environment.${envType}.redis-port")

    s"""
       |********************************************************************************
       |                              Heimdallr V.${version}
       |--------------------------------------------------------------------------------
       |    # Environment Type   : ${envType}
       |    # Redis Pub/Sub IP   : ${redisIP}
       |    # Redis Pub/Sub Port : ${redisPort}
       |********************************************************************************
     """.stripMargin
  }
}