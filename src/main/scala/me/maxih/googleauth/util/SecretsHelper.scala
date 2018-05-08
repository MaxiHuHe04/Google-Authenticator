package me.maxih.googleauth.util

import java.io.{BufferedOutputStream, File, FileOutputStream}

import com.github.windpapi4j.WinDPAPI
import javafx.scene
import javafx.scene.control.{Button => JFXButton}
import javafx.stage.{Stage => JFXStage}
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import me.maxih.googleauth.GoogleAuthenticator
import me.maxih.googleauth.GoogleAuthenticator._
import me.maxih.googleauth.fx.PINInputDialog
import org.apache.commons.codec.digest.DigestUtils
import scalafx.scene.control
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.control.{Alert, ButtonType}
import scalafx.scene.image.Image
import scalafx.scene.paint.Color

import scala.util.Try

/**
  * Created by Maxi H. on 07.05.2018
  */
private[googleauth] object SecretsHelper {

  var secretPassword: Option[SecretKeySpec] = None
  val winDPAPI: Option[WinDPAPI] = if (WinDPAPI.isPlatformSupported) Some(WinDPAPI.newInstance()) else None
  var extraSecure: Boolean = false


  def encryptSecret(secret: String, currentCounter: Int = -1, key: Option[SecretKeySpec] = secretPassword, forceKey: Boolean = false): Array[Byte] = {
    val original = currentCounter + "\u0000" + secret

    if (winDPAPI.isDefined && key.isEmpty && !forceKey) winDPAPI.get.protectData(original.getBytes())
    else if (key.isDefined) {
      val iv = Array[Byte](0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
      val ivSpec = new IvParameterSpec(iv)

      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(Cipher.ENCRYPT_MODE, key.get, ivSpec)

      cipher.doFinal(original.getBytes())
    } else original.getBytes() // No encryption
  }

  def decryptSecret(encrypted: Array[Byte], key: Option[SecretKeySpec] = secretPassword, onWrongPassword: => Unit = showWrongPassword(true), forceKey: Boolean = false): (Int, String) = {
    def decode(encoded: Array[Byte]): (Int, String) = {
      val split = new String(encoded).split("\u0000")
      (split(0).toInt, split(1))
    }


    if (winDPAPI.isDefined && key.isEmpty && !forceKey) decode(winDPAPI.get.unprotectData(encrypted))
    else if (key.isDefined) {
      val iv = Array[Byte](0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
      val ivSpec = new IvParameterSpec(iv)

      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(Cipher.DECRYPT_MODE, key.get, ivSpec)

      val decrypted = Try(cipher.doFinal(encrypted)).getOrElse({
        onWrongPassword
        System.exit(0)
        null
      })

      decode(decrypted)
    } else decode(encrypted) // No encryption
  }


  def askPassword(length: Int, text: String = "Please enter your PIN code to unlock Google Authenticator.", emptyAllowed: Boolean = false): Option[Option[SecretKeySpec]] = {
    val dialog = new PINInputDialog(length, text, emptyAllowed)

    dialog.showAndWait() match {
      case Some(pass: String) if pass.equals("") => Some(None)
      case Some(pass: String) =>
        if (extraSecure) Some(Some(new SecretKeySpec(DigestUtils.sha256(pass.getBytes()), "AES")))
        else Some(Some(new SecretKeySpec(DigestUtils.sha1(pass.getBytes()).splitAt(16)._1, "AES")))

      case _ => None
    }
  }

  def showWrongPassword(canReset: Boolean): Unit = {
    val resetButtonType = new control.ButtonType("RESET", ButtonData.OKDone)
    val alert = new Alert(AlertType.Error, "", control.ButtonType.Close) {

      this.dialogPane.value.lookupButton(ButtonType.Close).asInstanceOf[scene.control.Button].setDefaultButton(true)

      this.headerText = "Wrong password!"

      if (canReset) {
        this.contentText = "If you reset the accounts, you won't be able to log in to any of them!"
        this.buttonTypes.append(resetButtonType)

        val resetButton: JFXButton = this.dialogPane.value.lookupButton(resetButtonType).asInstanceOf[JFXButton]
        resetButton.setDefaultButton(false)
        resetButton.setTextFill(Color.DarkRed)
      }

    }

    if (stage != null) alert.initOwner(stage)
    else alert.dialogPane.value.getScene.getWindow.asInstanceOf[JFXStage].getIcons.add(new Image(getClass.getResourceAsStream("/icon.png")))

    alert.showAndWait() match {
      case Some(buttonType) if buttonType == resetButtonType =>
        val tmpBackupFile = File.createTempFile("GoogleAuthenticatorAccountResetBackup", ".bak")
        GoogleAuthenticator.preferences.exportNode(new BufferedOutputStream(new FileOutputStream(tmpBackupFile)))
        GoogleAuthenticator.preferences.clear()
        println("If this was accidentally, you can restore the accounts with this file:\n" + tmpBackupFile.getAbsolutePath)

      case _ =>
    }
  }


  implicit class HexBytes(self: Array[Byte]) {
    def toHexString: String = self.map("%02X".format(_)).mkString
  }


  def hexStringToBytes(hex: String): Array[Byte] = {
    val len = hex.length
    val data = new Array[Byte](len / 2)

    for (i <- 0 until len by 2) {
      data(i / 2) = ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16)).toByte
    }

    data
  }

}
