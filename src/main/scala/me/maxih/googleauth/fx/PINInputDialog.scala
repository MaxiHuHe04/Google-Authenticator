package me.maxih.googleauth.fx

import javafx.scene.Node
import javafx.stage.{Stage => JFXStage}
import me.maxih.googleauth.GoogleAuthenticator
import scalafx.application.Platform
import scalafx.geometry.Insets
import scalafx.scene.control.{ButtonType, Dialog, PasswordField}
import scalafx.scene.image.Image
import scalafx.scene.layout.{HBox, Priority}
import scalafx.scene.text.Font
import scalafx.stage.Window

/**
  * Created by Maxi H. on 30.04.2018
  */
object PINInputDialog {
  val defaultIcon = new Image(getClass.getResourceAsStream("/icon.png"))
}


class PINInputDialog(pinLength: Int, text: String, emptyAllowed: Boolean = false, owner: Window = GoogleAuthenticator.stage) extends Dialog[String] {
  if (owner != null) this.initOwner(owner)
  else this.dialogPane.value.getScene.getWindow.asInstanceOf[JFXStage].getIcons.add(PINInputDialog.defaultIcon)

  this.title = "Enter your PIN"
  this.headerText = text

  this.dialogPane.value.getButtonTypes.addAll(ButtonType.OK, ButtonType.Cancel)

  val okButton: Node = this.dialogPane.value.lookupButton(ButtonType.OK)
  if (!emptyAllowed) okButton.setDisable(true)

  val pinField: PasswordField = new PasswordField() {
    this.text.onChange((_, old, now) => {
      if (now.length > pinLength || !now.matches("\\d*")) this.text = old

      if (emptyAllowed && now.length == 0) okButton.setDisable(false)
      else if (now.length < pinLength) okButton.setDisable(true)
      else okButton.setDisable(false)
    })

    this.promptText = "PIN"
    this.prefWidth = pinLength * 10
    this.font = Font.font(13)
  }

  val hBox = new HBox()
  hBox.children.add(pinField)
  hBox.padding = Insets(20)
  hBox.hgrow = Priority.Always

  this.dialogPane.value.setContent(hBox)
  this.dialogPane.value.setPrefWidth(300)

  Platform.runLater(pinField.requestFocus())

  this.resultConverter = dialogButton =>
    if (dialogButton == ButtonType.OK) pinField.text.value
    else null

}
