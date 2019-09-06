package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash}

object AkkaRecap extends App {

  class SimpleActor extends Actor with ActorLogging with Stash {
    override def receive: Receive = {
      case "change" => context.become(anotherHandler)
      case "stashThis" => stash()
      case "unstashThis" =>
        unstashAll()
        context.become(anotherHandler)
      case "createChild" => context.actorOf(Props[SimpleActor], "myChild")
      case message => println(s"received $message")

    }

    def anotherHandler: Receive = {
      case "change" => context.become(anotherHandler)
      case message => println(s"received an other $message")
    }

    override def preStart(): Unit = {
      log.info("I am starting")
    }

    override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _ => Stop
    }

  }

  val system = ActorSystem("AkkaRecap")
  val actor = system.actorOf(Props[SimpleActor], "simpleActor")
  actor ! "hello"

  actor ! PoisonPill
}
