package part2_event_sourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.PersistentActor

object PersistentActors extends App{

  /*
  Accountant keeps track of invoices
   */

  //special messages
  case object Shotdown

  //COMMANDS
  case class Invoice(recipient: String, date:Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])

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
           // SAFE to acccess mutable state here, no other thread accessing the actor, although this is async
           latestInvoiceId += 1
           totalAmount += amount

           sender() ! "PerssitanceAck"
           log.info(s"persisted $e as invoice #${e.id}, for total amount $totalAmount")
        }
      case InvoiceBulk(invoices) => {
        /*
        1) create events
        2) persist all evetns,
        3)update the actor state when each event is persisted
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map{pair =>
          val id = pair._2
          val invoice = pair._1
          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }

        persistAll(events) { e =>
          latestInvoiceId += 1
          totalAmount += e.amount
          log.info(s"persisted SINGLE $e as invoice #${e.id}, for total amount $totalAmount")
        }
      }
      case "print" =>
        log.info(s"latest invoice amount is $totalAmount")
      case Shotdown =>
        context.stop(self)
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

        log.info(s"Recovered invoice #$id for amount $amount, total amount: $totalAmount")
    }

    /*
    This method is called, if persisting is failed.
    The actor will be STOPPED

    Best practice: start the actor again after a while, use Backoff supervisor
     */
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /*
    Called if the JOURNAL fails to persist the event.
    The actor is RESUMED
     */
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistantActors")
  val accountant = system.actorOf(Props[Accountant], "simpleActor")

//  for(i <- 1 to 10) {
//    accountant ! Invoice("The sofa company", new Date, i * 1000)
//  }



  /*
  Persisting multiple events

  persistAll
  */

  val invoices = for (i <- 1 to 5) yield Invoice("The awesome chairs", new Date, i * 2000)
  accountant ! InvoiceBulk(invoices.toList)


  /*
  NEVER CALL PERSIST OR PERSISTALL FROM FUTURES
   */

  /**
    * Shutdown of persistent actors
    */
  //poisenPill goes into a special letterbox, causing early shotdown, while shotdown message goes to the normal letterbox
  //accountant ! PoisonPill
  accountant ! Shotdown

  //define own stotdown
}
