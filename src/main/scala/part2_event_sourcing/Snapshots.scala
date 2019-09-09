package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.collection.mutable

object Snapshots extends App {

  //commands
  case class ReceivedMessage(content: String) //message from the contact
  case class SentMessage(content: String) //message to the contact

  //events
  case class ReceivedMessageRecord(id: Int, contents: String)
  case class SentMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String) = Props(new Chat(owner, contact))
  }
  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    val MAX_MESSAGES = 10

    var currentMessageId = 0
    val lastMessages = new mutable.Queue[(String, String)]()

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Received message: $contents")

          receiveMessage(contact, contents)

          currentMessageId += 1
        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Sent message: $contents")

          receiveMessage(owner, contents)

          currentMessageId += 1
        }
    }

    override def receiveRecover: Receive  = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"Received received message $id:contents")
        receiveMessage(contact, contents)
        currentMessageId = id
      case SentMessageRecord(id, contents) =>
        log.info(s"Received sent message $id:contents")
        receiveMessage(owner, contents)
        currentMessageId = id

    }

    private def receiveMessage(sender: String, contents: String) = {
      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, contact))
    }


  }

  val system = ActorSystem("SnapshotChat")
  val chat = system.actorOf(Chat.props("Barii", "Krozelia"))

//  for (i <- 1 to 100000) {
//    chat ! ReceivedMessage(s"Akka Rocks $i")
//    chat ! SentMessage(s"Akka Rules $i")
//  }
}
