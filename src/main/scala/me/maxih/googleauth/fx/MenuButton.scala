package me.maxih.googleauth.fx

import javafx.scene.input.MouseButton
import javafx.scene.layout.BackgroundPosition
import javafx.scene.paint.{Color => JFXColor}
import me.maxih.googleauth.Colors
import me.maxih.googleauth.util.Utils._
import scalafx.scene.control.Button
import scalafx.scene.image.Image
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{Background, BackgroundImage, CornerRadii}
import scalafx.scene.text.Font

/**
  * Created by Maxi H. on 30.04.2018
  */
class MenuButton(image: Image, animationColor: JFXColor = Colors.Button.AnimationBackground) extends Button {

  this.prefWidth.bind(height)

  this.font = Font.font(14)

  val img: BackgroundImage = backgroundImage(image, position = BackgroundPosition.CENTER)
  val defaultBackground = new Background(Array(img))
  val animBackground = new Background(Array(backgroundFill(animationColor, new CornerRadii(20))), Array(img))

  this.background = defaultBackground


  this.onMousePressed = event => if (event.getButton == MouseButton.PRIMARY) onPress(new MouseEvent(event))
  this.onMouseReleased = event => if (event.getButton == MouseButton.PRIMARY) onRelease(new MouseEvent(event))
  this.onMouseClicked = event => if (event.getButton == MouseButton.PRIMARY) onClicked(new MouseEvent(event))


  def onPress(event: MouseEvent): Unit =
    this.background = animBackground

  def onRelease(event: MouseEvent): Unit =
    this.background = defaultBackground

  def onClicked(event: MouseEvent): Unit = {}

}
