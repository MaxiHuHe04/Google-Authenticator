package me.maxih.googleauth.authentry

import javafx.scene.control.{Button => JFXButton}
import javafx.scene.input.MouseButton
import me.maxih.googleauth.api.OTP
import me.maxih.googleauth.util.Utils._
import me.maxih.googleauth.{Colors, GoogleAuthenticator}
import scalafx.application.Platform
import scalafx.beans.property.StringProperty
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, ButtonType, Label, TextInputDialog}
import scalafx.scene.input.{Clipboard, ClipboardContent, DataFormat}
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.Font
import scalafx.scene.{Group, Node}

/**
  * Created by Maxi H. on 23.04.2018
  */
trait AuthEntry extends AnchorPane {

  val otp: OTP
  val code: StringProperty = new StringProperty()

  val secret: String
  val accountName: StringProperty = new StringProperty()


  def update()

  def isTimed: Boolean

  def indicator: Node = new Label("Indicator")


  val codeLabel: Label = new Label() {
    this.font = new Font(30)

    this.topAnchor = 0
    this.bottomAnchor = 0
    this.rightAnchor = 60

    this.text.bind(code)
  }

  val sizeRect: Rectangle = new Rectangle() {
    this.height = 20
    this.width = 20
    this.fill = Color.Transparent
  }


  this.children = Seq(
    new Label(accountName.value) {
      this.font = new Font(30)
      this.font = Font.font(findFontSize)

      codeLabel.width.onChange((_, _, _) => this.font = Font.font(findFontSize))
      GoogleAuthenticator.stage.width.onChange((_, _, _) => this.font = Font.font(findFontSize))
      GoogleAuthenticator.stage.maximized.onChange((_, _, _) => this.font = Font.font(findFontSize))
      GoogleAuthenticator.getModeProp.onChange((_, _ ,_) => this.font = Font.font(findFontSize))


      this.topAnchor = 0
      this.bottomAnchor = 0
      this.leftAnchor = 20

      this.text.bind(accountName)


      def findFontSize: Double = {
        val fontSize = font.value.getSize
        val width = this.boundsInLocal.value.getWidth
        val margin = if (GoogleAuthenticator.getMode != GoogleAuthenticator.Mode.Normal) 40 else 0
        val maxWidth = GoogleAuthenticator.stage.width.value - codeLabel.width.value - 110 - margin

        val result = fontSize * maxWidth / width
        if ((width > maxWidth || result < 30) && result > 10) return result
        if (result > 30) return 30

        fontSize
      }
    },

    codeLabel,

    new Group(sizeRect) {
      this.bottomAnchor = 20
      this.rightAnchor = 20

      Platform.runLater(this.children.add(indicator))
    }
  )


  this.background = Colors.AuthEntry.Background
  this.maxHeight = 80
  this.vgrow = Priority.Always


  val deleteAlert = new Alert(AlertType.Warning,
    "Do you really want to remove this account?\n" +
      "You won't be able to restore it.\n\n" +
      "The confirmation won't get disabled automatically.\n" +
      "Make sure you disabled 2-factor authorization on this account.\n", ButtonType.Cancel, ButtonType.OK)
  deleteAlert.headerText = "Remove this account?"
  deleteAlert.dialogPane.value.lookupButton(ButtonType.OK).asInstanceOf[JFXButton].setDefaultButton(false)
  deleteAlert.dialogPane.value.lookupButton(ButtonType.Cancel).asInstanceOf[JFXButton].setDefaultButton(true)
  Platform.runLater(deleteAlert.initOwner(GoogleAuthenticator.stage))


  this.onMousePressed = event => if (event.getButton == MouseButton.PRIMARY) {
    this.background = Colors.AuthEntry.SecondaryBackground
  }

  this.onMouseReleased = event => if (event.getButton == MouseButton.PRIMARY) {
    if (GoogleAuthenticator.getMode == GoogleAuthenticator.Mode.Deleting)
      deleteAlert.showAndWait() match {
        case Some(ButtonType.OK) => GoogleAuthenticator.removeAccount(this)
        case _ =>
      }

    else if (GoogleAuthenticator.getMode == GoogleAuthenticator.Mode.Editing) {

      val renameDialog = new TextInputDialog(this.accountName.value)
      renameDialog.headerText = "Please enter a new name."
      renameDialog.initOwner(GoogleAuthenticator.stage)

      renameDialog.showAndWait() match {
        case Some(name) => this.accountName.value = name
        case _ =>
      }

    } else Clipboard.systemClipboard.content = ClipboardContent(DataFormat.PlainText -> code.value.replaceAll(" ", ""))

    this.background = Colors.AuthEntry.Background
  }




  private[googleauth] def padString(string: String, len: Int, padChar: Char, right: Boolean = false): String = {
    var result = string
    while (result.length < len) {
      if (right) result += padChar
      else result = padChar + result
    }
    result
  }

}
