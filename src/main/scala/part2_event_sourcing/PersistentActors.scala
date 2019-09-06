package part2_event_sourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActors extends App{

  /*
  Accountant keeps track of invoices
   */

  //COMMANDS
  case class Invoice(recipient: String, date:Date, amount: Int)

  //EVENTS
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  class Accountant extends PersistentActor with ActorLogging {
    //mutable to avoid context.become
    var latestInvoiceId = 0
    var totalAmount = 0

    def persistenceId: String = "simple-accountant" //make this unique

    /**
      * The normal receive method
     */
    def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
        When receive an event
        1) Create an EVENT and persist into the store
        2) persist the event, and pass in a callback that fill get called triggered once the event is written
        3) update the actor state when the event has persisted
         */
        log.info(s"Receive invoice for amount $amount")
        persist(InvoiceRecorded(latestInvoiceId, recipient, date, amount))
        /* time gap: all messages sent to this actor are stashed */
         { e =>
          // SAFE to acccess mutable state here, no other thread accessing the actor, althoug this is async
          latestInvoiceId += 1
          totalAmount += amount
          log.info(s"persisted $e as invoice #${e.id}, for total amount $totalAmount")

        }
      case "print" =>
        log.info(s"latest invoice amount is $totalAmount")
    }

    /**
      * Handler called on recovery
      */
    def receiveRecover: Receive = {
      /*
      Follow the logic in the persist steps of receiveCommand
       */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount

        sender() ! "PersistenceAck"
        log.info(s"Recovered invoice #$id for amount $amount, total amount: $totalAmount")
    }
  }

  val system = ActorSystem("PersistantActors")
  val accountant = system.actorOf(Props[Accountant], "simpleActor")

//  for(i <- 1 to 10) {
//    accountant ! Invoice("The sofa company", new Date, i * 1000)
//  }
}
