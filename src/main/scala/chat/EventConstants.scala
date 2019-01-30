package chat

import akka.actor._

//sealed trait InternalProtocol
object EventConstants {

  case object HeimdallrStop
  case object HeimdallrError
  case object HeimdallrView
  case object HeimdallrChatStatus
  case object HeimdallrFailover

  case object WebServiceStart
  case object WebServiceStop

  case object HealthUp
  case object HealthDown

  case object AggregatorView
  case object AggregatorValueToStats
  case object AggregatorCollectionToStats
  case class AggregatorRoomValueToStats(chatRoomID: Int)

  case class HeimdallrStart(args: Array[String])
  case class HeimdallrActorDown(actorRef: ActorRef)

  case class CreateChatRoom(roomId: Int)
  case class RegNodeInfor(hostName: String, port: Int)
  case class RegChatUser(roomId: Int, userActor: ActorRef)
  case class TermChatUser(roomId: Int, is_guest: Boolean, uid: Int, nick: String)
  case class JoinRoom(chatRoomActorRef: ActorRef)
  case class RemoveChatRoom(chatRoomID: Int)
  case class UpdateChatCount(chatRoomID: Int, users: Int, member: Int, guest: Int)

  case class RegActor(actorRef: ActorRef)
  case class RegProps(props: Props, name: String)
  case class StopActor(actorRef: ActorRef)
}

