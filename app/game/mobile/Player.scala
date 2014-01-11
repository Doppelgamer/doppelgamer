package game.mobile

import scala.math.BigDecimal.int2bigDecimal

import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import game.communications.PlayActorConnection
import game.communications.RetryingConnection.Ack
import game.communications.RetryingConnection.ToClient
import game.events.Event
import game.events.EventHandler
import game.events.Handle
import game.mobile.Mobile.Moved
import game.world.Room
import game.world.Room.RoomData

object Player {
  case class Invalid( s: String ) extends Event
  case class KeyUp( code: Int ) extends Event
  case class KeyDown( code: Int ) extends Event
  case class Click( x: Int, y: Int ) extends Event
  case object Quit extends Event

  /** Used to tell a Player Actor to start. The caller should wait for a confirmation */
  case object Start
}

/**
 * An asynchronous EventHandler that handles communication
 * with the client and also interacts with the game world.
 */
class Player( val name: String ) extends Mobile {
  import Player._
  import Mobile._

  /** Represents a RetryingActorConnection */
  val connection = context.actorOf( Props( new PlayActorConnection( self ) ), name = "connection" )

  val height = 4
  val width = 2

  //temporary:
  var position = newPosition( 10, 30 )

  val mobileBehavior: Receive = {
    case Start ⇒
      sender ! connection
      emit( Room.Arrived )
      logger.info( "joined the game" )
    case ack: Ack                ⇒ connection ! ack
    case Click( x: Int, y: Int ) ⇒
    case KeyUp( 81 )             ⇒ self ! PoisonPill
    case KeyDown( 32 | 38 | 87 ) ⇒ jump()
    case evt @ Moved( mobile, p, m ) if mobile == self ⇒
      move( evt )
      if ( !( m.x == 0 && m.y == 0 ) )
        connection ! ToClient( s""" { "type":"move", "id":"${self.path}", "position":[${position.x}, ${position.y}] } """ )
    case RoomData( refs ) ⇒
      for ( ref ← refs )
        connection ! ToClient(
          s""" {"type":"create", "id":"${ref.path}", 
    			"position":[${position.x},${position.y}],
    			"dimensions":[$width, $height] } """,
          true )
  }

  override val standing: Receive = {
    case KeyDown( 65 | 37 ) ⇒ moveLeft()
    case KeyDown( 68 | 39 ) ⇒ moveRight()
  }

  override val moving: Receive = {
    case KeyUp( 65 | 68 | 37 | 39 ) ⇒ stopMoving()
  }

  override def postStop {
    logger.info( s"terminated." )
    this.moveScheduler.cancel
    emit( Quit )
  }

}