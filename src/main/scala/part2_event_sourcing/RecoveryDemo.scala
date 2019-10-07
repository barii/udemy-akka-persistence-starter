package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {

    override def persistenceId: String = "recovery-actor"

    override def receiveCommand: Receive = online(0)


    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfully persisted $event, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
          context.become(online(latestPersistedEventId + 1))
        }
    }


    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        log.info("I have finished recovering")
      case Event(id, contents) =>
//        if (contents.contains("314"))
//          throw new RuntimeException("I can't take this anymore")
        log.info(s"Recovered: $contents, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")

        context.become(online(id+1))
        // This will NOT change the event handler during recovery
        // AFTER recorty the "normal" handler will be the result of ALL the stacking of context.becomes
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

    //override def recovery: Recovery = Recovery(toSequenceNr = 100)
    //override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.latest())
    //override def recovery: Recovery = Recovery.none
  }

  val system = ActorSystem("RecoverryDemo")
  val recoveryAvtor = system.actorOf(Props[RecoveryActor], "recoverySctor")

  for (i <- 1 to 1000) {
    recoveryAvtor ! Command(s"command $i")
  }
  //All commands sent during recovery are stashed

  /*
    2 - Failure during recovery
      - OnRecoveryFailure + the actor is STOPPED
   */

  /*
    3 - customized recovery
      - Do not persist more event after a customized _incomplete_ recovery,
   */

  /*
    4 - Recovery status - Knowing when you are done
      - getting a signal when you're done recoverying
   */

  /*
    5 - Stateless actors
   */
}
