package game.world

import scala.math.BigDecimal
import scala.math.BigDecimal.double2bigDecimal
import scala.math.BigDecimal.int2bigDecimal
import scala.math.hypot

import game.events.Adjust
import game.events.AdjustHandler
import game.mobile.Mobile.Moved
import game.mobile.Movement
import game.util.math.Intersection
import game.util.math.Line
import game.util.math.Point
import game.util.math.PointLike
import game.util.math.Vector

/**
 * A surface is a Line that can be either a Wall or a Floor. A Wall always has a vertical
 * (undefined) slope, while a Floor always has a Defined slope. A Surface is designed to
 * impede a Mobile's Movement. We do this by extending AdjustHandler and providing
 * Adjusts that alter Moved Events. Whatever uses a Surface can use the Adjust functions
 * however it wants. For example, a Room can include them in its 'outgoing' Adjust list:
 *
 * {{{
 * val floor = DoubleSided( Point( 0, 0 ), Point( 200, 0 ) )
 * outgoing = outgoing ::: floor.outgoing
 * }}}
 *
 * In fact, the RoomEventHandler does just that to make sure Mobiles don't fall into
 * infinity.
 */
object Surface {
  implicit val rm = BigDecimal.RoundingMode.HALF_UP
}

/**
 * A Surface is essentially just a line, owned by Room objects, that supplies Adjust
 * functions to modify Moved Events
 */
trait Surface extends Line with AdjustHandler {
  /**
   * Returns true if 'p' is within the X and Y bounds of this Surface. If you draw
   * a box around this Surface, with the Start & End points resting in the corners,
   * then 'p' must be within that box. If the Surface is flat, then 'p' must be
   * resting directly on the Surface.
   */
  def inBounds( p: PointLike ): Boolean =
    ( start.x <= p.x && p.x <= end.x ) && ( start.y <= p.y && p.y <= end.y )
}

trait Floor extends Surface {
  import Surface._

  /** Is p above this Floor? */
  def aboveFloor( p: PointLike ): Boolean = p.y > slope.m * p.x + b

  /** Is p below this Floor? */
  def belowFloor( p: PointLike ): Boolean = p.y < slope.m * p.x + b

  /**
   *  Is p directly on this Floor? We Round p.x and p.y to the nearest 5 decimal places here
   *  because very slight rounding errors (due to computer memory limits) will
   *  cause the function to return false when it probably should be true.
   */
  def onFloor( p: PointLike ): Boolean =
    p.y.setScale( 5, rm ) == ( slope.m * p.x + b ).setScale( 5, rm ) &&
      inBounds( p )

  /**
   * This function is defined for 2 cases:
   * 1) Mobile is standing on floor and wants to move below it:
   * Redirect the movement so that it has the same Slope as the Floor
   *
   * 2) Mobile is above the Floor and wants to move below it:
   * Keep the direction and Slope, but cut the distance short
   */
  val onCollision: Adjust = {
    // Mobile is standing on Floor and wants to move below it:
    case Moved( ar, p, mv ) if {
      onFloor( p.feet ) && belowFloor( Point( p.feet.x + mv.x, p.feet.y + mv.y ) )
    } ⇒
      val newMv =
        if ( mv.x == 0 ) Movement( 0, 0 ) // Mobile is simply standing still
        else { // Mobile is moving forward
          val k = hypot( slope.dx.toDouble, slope.dy.toDouble ) / mv.x
          Movement( slope.dx / k, slope.dy / k )
        }
      Moved( ar, p, newMv )

    // Mobile is above the Floor and wants to move below it:
    case Moved( ar, p, mv ) if {
      aboveFloor( p.feet ) && belowFloor( Point( p.feet.x + mv.x, p.feet.y + mv.y ) )
    } ⇒
      val vector = Vector( Point( p.feet.x, p.feet.y ), Point( p.feet.x + mv.x, p.feet.y + mv.y ) )
      val inter = new Intersection( vector, this )
      val newMovement =
        if ( inBounds( inter ) ) Movement( inter.x - p.x, inter.y - p.feet.y )
        else mv
      Moved( ar, p, newMovement )
  }

  outgoing = Set( onCollision )
}

case class Wall( xpos: Int,
                 ytop: Int,
                 ybottom: Int ) extends Surface {
  lazy val start = Point( xpos, ytop )
  lazy val end = Point( xpos, ybottom )

  val stop: Adjust = {
    case Moved( ar, p, m ) if inBounds( p.left ) || inBounds( p.right ) ⇒
      Moved( ar, p, Movement( 0, m.y ) )
  }

  //    adjusts = adjusts ::: List( stopLeft, stopRight )
}

case class SingleSided( val start: Point, val end: Point ) extends Floor

case class DoubleSided( val start: Point, val end: Point ) extends Floor