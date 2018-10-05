package chat

import akka.actor._

import scala.concurrent.ExecutionContext
import com.redis.RedisClient

object ChatRoomActor {
  case object Join
  case class ChatMessage(message: String)
}

class ChatRoomActor extends Actor {
  implicit val executionContext: ExecutionContext = context.dispatcher

  import ChatRoomActor._
  var users: Set[ActorRef] = Set.empty

  val r = new RedisClient("localhost", 6379)
  r.set("key1", "abc")

  println("subscribe channel: chat")

  def receive = {
    case Join =>
      users += sender()
      // we also would like to remove the user when its actor is stopped
      context.watch(sender())

    case Terminated(user) =>
      users -= user

    case msg: ChatMessage =>
      users.foreach(_ ! msg)
  }
}
