package me.maxih.googleauth.authentry

import javafx.scene.input.MouseButton
import javafx.scene.{paint, shape => jfxshape}
import me.maxih.googleauth.Colors
import me.maxih.googleauth.api.HOTP
import me.maxih.googleauth.util.Scheduler
import scalafx.scene.paint.Color
import scalafx.scene.shape._
import scalafx.scene.{Group, Node}

/**
  * Created by Maxi H. on 23.04.2018
  */
case class CounterAuthEntry(name: String, secret: String, start: Int = 0, length: Int = 6) extends AuthEntry {
  val otp = new HOTP(secret, length, start)

  accountName.value = name

  override def update(): Unit =
    this.code.value = padString(otp.getAuthCode.toString, length, '0').splitAt(3).productIterator.mkString(" ")

  override def isTimed: Boolean = false

  override def indicator: Node = reloadGroup

  val reloadRing: Arc = ring(10, 10, 8, 2.5, 85, 260, Color.Transparent, Colors.AuthEntry.IndicatorColor)

  val reloadArrow = Polygon(
    12, -1,
    9, 9,
    15, 5
  )
  reloadArrow.fill = Colors.AuthEntry.IndicatorColor

  val reloadGroup = new Group(reloadRing, reloadArrow)


  val enableScheduler = Scheduler()

  def reload(): Unit = {
    this.otp.next()
    this.update()
    println(this.otp.counter)
    reloadGroup.disable = true

    val color = new Color(reloadRing.stroke.value.asInstanceOf[paint.Color])

    reloadRing.stroke = color.brighter
    reloadArrow.fill = color.brighter

    this.enableScheduler.schedule({
      reloadGroup.disable = false
      reloadRing.stroke = color
      reloadArrow.fill = color
    }, 5)
  }


  reloadRing.onMousePressed = event => if (event.getButton == MouseButton.PRIMARY) reload()
  reloadArrow.onMousePressed = event => if (event.getButton == MouseButton.PRIMARY) reload()



  def ring(centerX: Double, centerY: Double, radius: Double, width: Double, start: Double, length: Double, bgColor: Color, color: Color): Arc =
    new Arc(new jfxshape.Arc(centerX, centerY, radius, radius, start, length)) {
      this.`type` = ArcType.Open
      this.strokeWidth = width
      this.strokeType = StrokeType.Inside
      this.stroke = color
      this.fill = bgColor
    }

}
