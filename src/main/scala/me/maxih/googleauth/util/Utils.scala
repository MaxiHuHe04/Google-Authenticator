package me.maxih.googleauth.util

import javafx.scene.layout
import javafx.scene.paint.{Color => JFXColor}
import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.image.Image
import scalafx.scene.layout._
import scalafx.scene.paint.Color

import scala.language.implicitConversions

/**
  * Created by Maxi H. on 23.04.2018
  */
object Utils {

  def backgroundFill(color: JFXColor, cornerRadii: layout.CornerRadii = CornerRadii.Empty, insets: Insets = Insets.Empty): BackgroundFill =
    new BackgroundFill(new Color(color), new CornerRadii(cornerRadii), insets)

  def backgroundImage(image: Image, repeatX: BackgroundRepeat = BackgroundRepeat.NoRepeat, repeatY: BackgroundRepeat = BackgroundRepeat.NoRepeat, position: layout.BackgroundPosition = BackgroundPosition.Default, size: BackgroundSize = BackgroundSize.Default): BackgroundImage =
    new BackgroundImage(image, repeatX, repeatY, new BackgroundPosition(position), size)

  implicit def color2Background(color: Color): Background = new Background(Array(backgroundFill(color)))
  implicit def img2Background(image: Image): Background = new Background(Array(backgroundImage(image)))


  implicit class AnchoredNode(self: Node) {
    def topAnchor: Double = AnchorPane.getTopAnchor(self)
    def topAnchor_=(value: Double): Unit = AnchorPane.setTopAnchor(self, value)

    def leftAnchor: Double = AnchorPane.getLeftAnchor(self)
    def leftAnchor_=(value: Double): Unit = AnchorPane.setLeftAnchor(self, value)

    def bottomAnchor: Double = AnchorPane.getBottomAnchor(self)
    def bottomAnchor_=(value: Double): Unit = AnchorPane.setBottomAnchor(self, value)

    def rightAnchor: Double = AnchorPane.getRightAnchor(self)
    def rightAnchor_=(value: Double): Unit = AnchorPane.setRightAnchor(self, value)
  }

}
