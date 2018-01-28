package reactive1

import java.lang.Math
import java.util.Random

import akka.actor.Actor
import akka.actor.Stash
import akka.actor.ActorRef
import akka.actor.Props
import akka.event.Logging
import akka.event.LoggingReceive
import akka.actor.ActorSystem
import scala.concurrent.duration._
import scala.concurrent.Await

// Assignment: 
// - implement solution to the producers/consumers problem 
//   using the actor model / Akka
// - test the correctness of the created solution for multiple
//   producers and consumers
// Hint: use akka.actor.Stash

// object PC contains messages for all actors -- add new if you need them 
object PC {
  case class Init()
  case class Produce(x: Long)
  case class Consume()
  case class ProduceDone(x: Long)
  case class ConsumeDone(x: Long)
  case class Finish()
}

class Producer(name: String, count: Int, range: Int, buf: ActorRef) extends Actor {
  import PC._
  
  var counter = count

  def receive = {
    case "Init" => {
      counter -= 1
      buf ! Produce(getVal())
    }
    case ProduceDone(x) => {
      println(x + " produced.")
      
      if (counter == 0){
        println("Producent is done")
        buf ! Finish()
        context.stop(self)
      }else{
        counter -= 1
        buf ! Produce(getVal())
      }
    }
  }
  
  def getVal() = {
    new Random().nextInt(range) + 1;
  }

}

class Consumer(name: String, count: Int, range: Int, buf: ActorRef) extends Actor {
  import PC._
  
  var counter = count

  def receive = {
    case "Init" => {
      counter -= 1
      buf ! Consume()
    }
    case ConsumeDone(x) => {
      println(x + " consumed.")
      
      if (counter == 0){
        println("Consumer is done")
        buf ! Finish()
        context.stop(self)
      }else{
        counter -= 1
        buf ! Consume()
      }
    }
  }

}


class Buffer(n: Int, var m:Int) extends Actor with Stash{
  import PC._

  private val buf = new Array[Long](n)
  private var count = 0

  def receive = LoggingReceive {
    case Produce(x) => {
      if(count < n){
        buf.update(count, x)
        count += 1
        sender ! ProduceDone(x)
        unstashAll()
      }else{
        stash()
      }
    }
    case Consume() => {
      if(count > 0){
        count -= 1
        sender ! ConsumeDone(buf.apply(count))
        unstashAll()
      }else{
        stash()
      }
    }
    case Finish() => {
      m -= 1;
      if(m == 0){
        println("All done.")
        context.system.terminate
      }
    }
  }
}


object ProdCons extends App {
  import PC._
  
  val system = ActorSystem("ProdKons")
  val n = 1000
  val repeats = 1000
  val range = 1000
  
  val buffer = system.actorOf(Props(classOf[Buffer], 100, 2*n))
  
  var i = 0
  for (i <- 1 to n){
    val consumer = system.actorOf(Props(classOf[Consumer], "consumer" + i, repeats, range, buffer))
    val producer = system.actorOf(Props(classOf[Producer], "producer" + i, repeats, range, buffer))
    
    consumer ! "Init"
    producer ! "Init"
  }
  
  Await.result(system.whenTerminated, Duration.Inf)
}