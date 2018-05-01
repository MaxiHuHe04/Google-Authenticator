package me.maxih.googleauth.authentry

import javafx.scene.paint
import me.maxih.googleauth.Colors
import me.maxih.googleauth.api.TOTP
import scalafx.application.Platform
import scalafx.beans.property.{IntegerProperty, ObjectProperty}
import scalafx.scene.Node
import scalafx.scene.shape.{Arc, ArcType}

/**
  * Created by Maxi H. on 23.04.2018
  */
case class TimedAuthEntry(name: String, secret: String, length: Int = 6) extends AuthEntry {
  val otp = new TOTP(secret, length)

  accountName.value = name

  val remaining = new IntegerProperty()
  val remainingColor = new ObjectProperty[paint.Paint]()

  this.codeLabel.textFill.bind(remainingColor)


  override def update(): Unit = {
    this.code.value = padString(otp.getAuthCode.toString, length, '0').splitAt(3).productIterator.mkString(" ")
    this.remaining.value = otp.getRemaining
    this.remainingColor.value = this.remaining.value match {
      case x if x <= 4 => if (x % 2 == 0) Colors.AuthEntry.TimeLowestTextColor else Colors.AuthEntry.TimeLowestTextColor2
      case x if x <= 8 => Colors.AuthEntry.TimeLowTextColor
      case _ => Colors.AuthEntry.TimeNormalTextColor
    }
  }

  override def isTimed: Boolean = true

  override def indicator: Node = new Arc() {
    val radius = 8
    val rectSize = 20

    this.radiusX = radius
    this.radiusY = radius
    this.centerX = rectSize / 2
    this.centerY = rectSize / 2

    this.startAngle = 90

    this.length.bind(remaining / 30.0 * 360)
    this.`type` = ArcType.Round

    this.fill = Colors.AuthEntry.IndicatorColor

    Platform.runLater(this.requestFocus())
  }


}
