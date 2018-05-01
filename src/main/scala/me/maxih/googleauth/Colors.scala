package me.maxih.googleauth

import scalafx.scene.paint.Color
import javafx.scene.paint.{Color => JFXColor}

import scala.language.implicitConversions

/**
  * Created by Maxi H. on 30.04.2018
  */
object Colors {

  val Background: Color = Color.Gainsboro
  val SecondaryBackground: Color = Color.rgb(66, 133, 244).brighter

  object AuthEntry {
    val Background: Color = Color.White
    val SecondaryBackground: Color = Color.rgb(240, 240, 240, 1)

    val TimeNormalTextColor: Color = Color.rgb(0x29, 0x62, 0xFF)
    val TimeLowTextColor: Color = Color.rgb(0xFF, 0xC1, 0x07)
    val TimeLowestTextColor: Color = Color.Red
    val TimeLowestTextColor2: Color = Color.rgb(0xFF, 0x55, 0x55)

    val IndicatorColor: Color = Color.Gray
  }

  object Button {
    val AnimationBackground: Color = Color.WhiteSmoke.opacity(0.2)
    val AnimationBackgroundDelete: Color = Color.rgb(0xD3, 0x2F, 0x2F).opacity(0.9)
  }

  implicit def jfxColor2Color(color: JFXColor): Color = new Color(color)

}
