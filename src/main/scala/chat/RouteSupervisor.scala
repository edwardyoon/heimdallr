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

import java.util.concurrent.TimeUnit
import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, ActorSystem, Identify, OneForOneStrategy, Props}
import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import chat.admin.AdminService
import chat.EventConstants._

object environment {
  var system: ActorSystem = null
  var aggregator: ActorRef = null
  var version = ""
  var envType = ""
  var serviceMode: String = "running"
  var hostName: String = "localhost"
  var port: Int = 8000

  def getHeimdallrSystem():ActorSystem = {
    system
  }

  def setEnvType(str: String):Unit = {
    envType = str
  }

  def getEnvType():String = {
    envType
  }

  def setServiceMode(mode: String) = {
    serviceMode = mode
  }

  def getServiceMode(): String = {
    serviceMode
  }
}

/**
  * This supervisor is a grand parent actor and handles whole sub services
  * @param actorSystem
  */
class RouteSupervisor(actorSystem: ActorSystem) extends Actor with ActorLogging {
  implicit val system = actorSystem
  implicit val executionContext: ExecutionContext = context.dispatcher

  var env: String  = null
  var cs: ActorRef = null
  var ws: ActorRef = null
  var as: ActorRef = null
  var hs: ActorRef = null

  override val supervisorStrategy = OneForOneStrategy(
    maxNrOfRetries = 10,
    withinTimeRange = Duration.create(10, TimeUnit.MINUTES),
    loggingEnabled = true) {
      case x: Exception =>
        log.info("FIXME: RouteSupervisor => " + x.toString)
        Resume
      case t =>
      super.supervisorStrategy.decider.applyOrElse( t, (_:Any) => Escalate )
  }

  override def receive: Receive = {
    case HeimdallrStart(args) =>
      ArgsValidation(args)
      environment.setEnvType(env)
      environment.system = system
      environment.version = system.settings.config.getString("akka.heimdallr-version" )
      environment.aggregator = context.actorOf(Props[Aggregator], "aggregator")
      context.watch(environment.aggregator)
      hs = context.actorOf(Props[HealthyService], "hs")
      context.watch(hs)
      cs = context.actorOf(Props(classOf[ChatSupervisor], env), "cs")
      context.watch(cs)
      ws = context.actorOf(Props(classOf[ChatService], cs), "ws")
      context.watch(ws)
      as = context.actorOf(Props(classOf[AdminService], cs), "as")
      context.watch(as)


      HeimdallrLogo()

    case RegNodeInfor(hostName, port) =>
      setNodeInfor(hostName, port)

    case HealthUp =>
      hs ! WebServiceStart

    case HealthDown =>
      hs ! WebServiceStop

    case HeimdallrView =>
      ViewActorProperties()

    case RegActor(actorRef) =>
      context.watch(actorRef)

    case StopActor(actorRef) =>
      context.stop(actorRef)

    case HeimdallrActorDown(actorRef) =>
      context.stop(actorRef)

    // *** supervisor ! "akka://heimdallr/user/{Valid ActorName}"
    case path: String =>
      log.debug(s"checking path => $path")
      context.actorSelection(path) ! Identify(path)

    case ActorIdentity(path, Some(ref)) =>
      log.debug(s"found actor $ref on $path")

    // *** supervisor ! "/user/{Invalid ActorName}"
    case ActorIdentity(path, None) =>
      log.debug(s"could not find an actor on $path")

    case x =>
      log.warning("RouteSupervisor Unknown message : " + x)
  }

  /**
    * @param hostName, port
    */
  private def setNodeInfor(hostName: String, port: Int): Unit = {
    environment.hostName = hostName
    environment.port = port
  }

  private def ViewActorProperties() = {
    self ! "akka://heimdallr/user/Supervisor/cs/*"
    self ! "akka://heimdallr/user/Supervisor/ws/*"
  }

  private def ArgsValidation(args: Array[String]) = {
    env = args.length match {
      case 1 => args(0) match {
        case "live" => args(0)
        case "standby" => args(0)
        case "dev" => "development"
        case "development" => args(0)
        case _ => Bye()
      }

      case _ => Bye()
    }
  }

  private def HeimdallrLogo() = {
    log.info( "********************************************************************" )
    log.info(s"                         Heimdallr V.${environment.version}" )
    log.info( "--------------------------------------------------------------------" )
    log.info( "# Environment Type   : " + env )
    log.info( "# Redis Pub/Sub IP   : " + system.settings.config.getString(s"akka.environment.${env}.redis-ip"))
    log.info( "# Redis Pub/Sub Port : " + system.settings.config.getString(s"akka.environment.${env}.redis-port"))
    log.info( "# API Server         : " + environment.getEnvType )
    log.info( "********************************************************************" )
  }

  private def Bye() = {
    log.info("\nUsage : ")
    log.info(" - sbt \"run [live|standby|development or dev]\"\n")
    context.system.terminate()
    null
  }
}

