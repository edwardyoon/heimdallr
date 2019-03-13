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

import akka.http.scaladsl.server.Directives._
import EventConstants._

/**
  * This service is used for communicating heartbeat message with load balancer
  */
class HealthyService extends WebServiceActor {
  val servicePort = 8099
  val serviceRoute= //<- adjustable depended on client url
    get {
      pathEndOrSingleSlash {
        complete("Welcome to Heimdallr")
      }
    }

  override def preStart(): Unit = {
    log.info( "Staring Health Service ..." )
    serviceBind(serviceRoute, servicePort)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.info( "Restarting Health Service ..." )
    preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info( "Health Service has restarted." )
  }

  override def postStop(): Unit = {
    serviceUnbind()
    log.info( "Health Service Down !" )
  }

  override def receive: Receive = {
    case WebServiceStart =>
      serviceBind(serviceRoute, servicePort)
    case WebServiceStop =>
      serviceUnbind()
    case x =>
      log.warning("HealthyService Unknown message : " + x)
  }
}

