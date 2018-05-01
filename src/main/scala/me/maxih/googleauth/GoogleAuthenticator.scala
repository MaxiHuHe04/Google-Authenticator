package me.maxih.googleauth

import java.awt.{SystemTray, TrayIcon}
import java.io._
import java.util.prefs.Preferences

import com.github.windpapi4j.WinDPAPI
import javafx.collections.ListChangeListener
import javafx.scene
import javafx.scene.control.{Button => JFXButton}
import javafx.scene.{Node => JFXNode}
import javafx.stage.{Stage => JFXStage}
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.imageio.ImageIO
import me.maxih.googleauth.authentry.{AuthEntry, CounterAuthEntry, TimedAuthEntry}
import me.maxih.googleauth.fx.PINInputDialog
import me.maxih.googleauth.util.Scheduler
import me.maxih.googleauth.util.Utils._
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.scene.{Scene, control}
import scalafx.stage.FileChooser

import scala.util.Try


/**
  * Created by Maxi H. on 11.04.2018
  */
object GoogleAuthenticator extends JFXApp {
  val accounts = new ObservableBuffer[AuthEntry]()
  var secretPassword: Option[SecretKeySpec] = None
  val winDPAPI: Option[WinDPAPI] = if (WinDPAPI.isPlatformSupported) Some(WinDPAPI.newInstance()) else None

  val mode: ObjectProperty[Mode.Mode] = new ObjectProperty[Mode.Mode](null, "", Mode.Normal)


  // Requires Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files
  val EXTRA_SECURE_SECRET_KEY: Boolean = parameters.unnamed.contains("--extra-secure") || parameters.unnamed.contains("-s")


  if (winDPAPI.isEmpty || parameters.unnamed.contains("--force-pass") || parameters.unnamed.contains("-p")) this.secretPassword = Some(askSecretPassword(4))


  stage = new PrimaryStage {
    width = 400
    height = 600
    minWidth = 300
    minHeight = 200
    title = "Google Authenticator"
    JFXApp.AutoShow = false

    val windowIcon = new Image(this.getClass.getResourceAsStream("/icon.png"))
    icons.add(windowIcon)


    scene = new Scene {

      root = new BorderPane {
        this.background = Colors.Background


        bottom = new HBox(5) {
          children = Seq(
            new fx.MenuButton(new Image(getClass.getResourceAsStream("/burger.png"))) {
              val aboutDialog = new Alert(AlertType.Information, "", control.ButtonType.Close)
              aboutDialog.headerText = "About"
              aboutDialog.title = "Google Authenticator for PC"
              aboutDialog.contentText =
                """
                  |Cross-platform Google Authenticator made by Maxi Herczegh
                  |
                  |This program uses WinDPAPI from Peter G. Horvath and AES on non-windows systems to protect the secrets.
                  |
                  |Rubbish bin icon made by Freepik from www.flaticon.com.
                  |Program icon made by Google
                """.stripMargin

              Platform.runLater(aboutDialog.initOwner(stage))

              override def onClicked(event: MouseEvent): Unit = aboutDialog.showAndWait()
            },

            new fx.MenuButton(new Image(getClass.getResourceAsStream("/backup.png"))) {
              val Save = new ButtonType("Save")
              val Load = new ButtonType("Load")

              val backupDialog = new Alert(AlertType.Confirmation, "", Save, Load, ButtonType.Cancel)
              backupDialog.headerText = "What do you want to do?"
              backupDialog.contentText = "Have in mind that all accounts will be overwritten when you load a backup!"

              Platform.runLater(backupDialog.initOwner(stage))


              val backupFileChooser = new FileChooser()
              backupFileChooser.extensionFilters.add(new FileChooser.ExtensionFilter("Backup", "*.xml"))

              override def onClicked(event: MouseEvent): Unit = {
                val prefs = preferences

                backupDialog.showAndWait() match {
                  case Some(Save) =>
                    // TODO: Save with AES to allow loading on other computers
                    // TODO: Own file format instead of exporting node (encrypted)
                    // TODO: Convert it with PIN to WinDPAPI on loading (only on windows)

                    val file = backupFileChooser.showSaveDialog(stage)
                    if (file != null) prefs.exportNode(new BufferedOutputStream(new FileOutputStream(file)))

                  case Some(Load) =>
                    val file = backupFileChooser.showOpenDialog(stage)
                    if (file != null) {
                      val inputStream = new BufferedInputStream(new FileInputStream(file))
                      saveAccounts()
                      accounts.clear()
                      Preferences.importPreferences(inputStream)
                      prefs.flush()
                      loadAccounts()
                    }

                  case _ =>
                }

              }
            },

            new Region {
              this.hgrow = Priority.Always
            },

            new fx.MenuButton(new Image(getClass.getResourceAsStream("/edit.png"))) {

              mode.onChange((_, _, now) =>
                if (now == Mode.Editing) this.background = this.animBackground
                else this.background = this.defaultBackground
              )

              override def onClicked(event: MouseEvent): Unit = {
                mode.value = mode.value ! Mode.Editing
              }
            },

            new fx.MenuButton(new Image(getClass.getResourceAsStream("/rubbish-bin.png")), Colors.Button.AnimationBackgroundDelete) {

              mode.onChange((_, _, now) =>
                if (now == Mode.Deleting) this.background = this.animBackground
                else this.background = this.defaultBackground
              )

              override def onClicked(event: MouseEvent): Unit = {
                mode.value = mode.value ! Mode.Deleting
              }

            },


            new fx.MenuButton(new Image(getClass.getResourceAsStream("/cross.png"))) {

              case class AccountResult(name: String, secret: String, timeBased: Boolean)

              val accountAddDialog: Dialog[AccountResult] = new Dialog[AccountResult]() {
                this.title = "Add 2-factor account"
                this.headerText = "Please type in the account name and secret key."

                Platform.runLater(this.initOwner(stage))

                this.dialogPane.value.getButtonTypes.addAll(ButtonType.OK, ButtonType.Cancel)

                val grid = new GridPane()
                grid.hgap = 10
                grid.vgap = 10
                grid.padding = Insets(20, 150, 10, 10)

                val nameField = new TextField()
                nameField.promptText = "Account name"

                val secretField = new TextField()
                secretField.promptText = "abcd efgh ijkl mnop"

                val timeBasedSwitch = new ToggleButton("Time based")
                timeBasedSwitch.selected = true

                grid.add(Label("Account name:"), 0, 0)
                grid.add(nameField, 1, 0)
                grid.add(Label("Secret:"), 0, 1)
                grid.add(secretField, 1, 1)
                grid.add(timeBasedSwitch, 1, 2)

                val okButton: JFXNode = this.dialogPane.value.lookupButton(ButtonType.OK)
                okButton.setDisable(true)

                nameField.text.onChange((_, _, _) => okButton.setDisable(!isValid))
                secretField.text.onChange((_, _, _) => okButton.setDisable(!isValid))

                def isValid: Boolean = {
                  if (nameField.text.value.trim.isEmpty || secretField.text.value.trim.isEmpty) return false
                  if (nameField.text.value.trim.equalsIgnoreCase("accounts") || nameField.text.value.contains(";;;;")) return false // conflict with saveAccounts key

                  for (account <- accounts if account.accountName.value.trim.equals(nameField.text.value.trim)) return false

                  if (new Base32().decode(secretField.text.value).isEmpty) return false

                  true
                }

                this.dialogPane.value.setContent(grid)

                Platform.runLater(nameField.requestFocus())

                this.resultConverter = dialogButton =>
                  if (dialogButton == control.ButtonType.OK) {
                    val res = AccountResult(nameField.text.value, secretField.text.value, timeBasedSwitch.selected.value)

                    this.nameField.text = ""
                    this.secretField.text = ""
                    this.timeBasedSwitch.selected = true

                    res
                  }
                  else null
              }

              override def onClicked(event: MouseEvent): Unit = {
                mode.value = Mode.Normal

                val result = accountAddDialog.showAndWait()

                result match {
                  case Some(AccountResult(name, secret, timeBased)) =>
                    if (timeBased) addAccount(TimedAuthEntry(name, secret))
                    else addAccount(CounterAuthEntry(name, secret))

                  case _ =>
                }
              }

            }

          )

          this.background = Colors.SecondaryBackground
          this.padding = Insets(2)

        }

        center = new VBox {
          this.vgrow = Priority.Always

          accounts.addListener(new ListChangeListener[AuthEntry] {
            override def onChanged(c: ListChangeListener.Change[_ <: AuthEntry]): Unit = {
              while (c.next()) {
                if (c.wasAdded()) c.getAddedSubList.forEach(entry => children.add(entry))
                if (c.wasRemoved()) c.getRemoved.forEach(entry => children.remove(entry))
              }
            }
          })

          mode.onChange((_, _, now) =>
            if (now != Mode.Normal) this.margin = Insets(20)
            else this.margin = Insets(0)
          )

          this.spacing = 1

        }
      }
    }

  }

  loadAccounts()


  Scheduler().scheduleAtRate(Platform.runLater(accounts.forEach(_.update())), 0, 1)


  initSystemTray()

  if (!parameters.unnamed.contains("--tray") && !parameters.unnamed.contains("-t")) stage.show()


  def getMode: Mode.Mode = mode.value

  def getModeProp: ObjectProperty[Mode.Mode] = mode

  def addAccount(account: AuthEntry): Unit = {
    accounts.add(account)
    saveAccounts()
  }

  def removeAccount(account: AuthEntry): Unit = {
    accounts.remove(account)
    saveAccounts()
  }

  def loadAccounts(): Unit = accounts.appendAll(getAccounts.map({
    case (name, (count, secret)) if count >= 0 => CounterAuthEntry(name, secret, count)
    case (name, (_, secret)) => TimedAuthEntry(name, secret)
  }))

  private def getAccounts: Map[String, (Int, String)] = {
    val prefs = preferences
    val keys = prefs.get("accounts", ";;;;").split(";;;;")

    keys.iterator.map(k => k -> decryptSecret(prefs.getByteArray(k, Array()))).toMap
  }

  private def saveAccounts(): Unit = {
    val prefs = preferences

    prefs.clear()

    prefs.put("accounts", accounts.map(_.accountName.value).mkString(";;;;"))
    if (prefs.get("accounts", "") == "") prefs.remove("accounts")


    for (account <- accounts) {
      val currentCounter = if (account.isTimed) -1 else account.asInstanceOf[CounterAuthEntry].otp.counter

      prefs.putByteArray(account.accountName.value, encryptSecret(account.secret, currentCounter))
    }

  }

  private def preferences: Preferences = Preferences.userNodeForPackage(getClass)


  private def encryptSecret(secret: String, currentCounter: Int = -1): Array[Byte] = {
    val original = currentCounter + "\0" + secret

    if (winDPAPI.isDefined && secretPassword.isEmpty) winDPAPI.get.protectData(original.getBytes())
    else if (secretPassword.isDefined) {
      val iv = Array[Byte](0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
      val ivSpec = new IvParameterSpec(iv)

      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(Cipher.ENCRYPT_MODE, secretPassword.get, ivSpec)

      cipher.doFinal(original.getBytes())
    } else throw new IllegalStateException("No secret password!")
  }

  private def decryptSecret(encrypted: Array[Byte]): (Int, String) = {
    def decode(encoded: Array[Byte]): (Int, String) = {
      val split = new String(encoded).split("\0")
      (split(0).toInt, split(1))
    }

    if (winDPAPI.isDefined && secretPassword.isEmpty) decode(winDPAPI.get.unprotectData(encrypted))
    else if (secretPassword.isDefined) {
      val iv = Array[Byte](0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
      val ivSpec = new IvParameterSpec(iv)

      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(Cipher.DECRYPT_MODE, secretPassword.get, ivSpec)

      val decrypted = Try(cipher.doFinal(encrypted)).getOrElse(wrongPassword())

      decode(decrypted)
    } else throw new IllegalStateException("No secret password!")
  }


  private def askSecretPassword(length: Int): SecretKeySpec = {
    val dialog = new PINInputDialog(length)
    dialog.dialogPane.value.getScene.getWindow.asInstanceOf[JFXStage].getIcons.add(new Image(getClass.getResourceAsStream("/icon.png")))
    dialog.showAndWait() match {
      case Some(pass: String) =>
        if (EXTRA_SECURE_SECRET_KEY) new SecretKeySpec(DigestUtils.sha256(pass.getBytes()), "AES")
        else new SecretKeySpec(DigestUtils.sha1(pass.getBytes()).splitAt(16)._1, "AES")

      case _ =>
        System.exit(0)
        null
    }
  }

  private def wrongPassword(): Nothing = {
    val resetButtonType = new control.ButtonType("RESET", ButtonData.OKDone)
    val alert = new Alert(AlertType.Error, "", resetButtonType, control.ButtonType.Close) {

      val resetButton: JFXButton = this.dialogPane.value.lookupButton(resetButtonType).asInstanceOf[JFXButton]
      resetButton.setDefaultButton(false)
      resetButton.setTextFill(Color.DarkRed)

      this.dialogPane.value.lookupButton(ButtonType.Close).asInstanceOf[scene.control.Button].setDefaultButton(true)

      this.headerText = "Wrong password!"
      this.contentText = "If you reset the accounts, you won't be able to log in to any of them!"

      this.dialogPane.value.getScene.getWindow.asInstanceOf[JFXStage].getIcons.add(new Image(getClass.getResourceAsStream("/icon.png")))

    }

    alert.showAndWait() match {
      case Some(buttonType) if buttonType == resetButtonType =>
        accounts.clear()

        val tmpBackupFile = File.createTempFile("GoogleAuthenticatorAccountResetBackup", ".xml")
        preferences.exportNode(new BufferedOutputStream(new FileOutputStream(tmpBackupFile)))
        preferences.clear()
        println("If this was accidentally, you can restore the accounts with this file:\n" + tmpBackupFile.getAbsolutePath)

      case _ =>
    }

    System.exit(0)
    throw new RuntimeException("Wrong password!")
  }


  private def initSystemTray(): Option[TrayIcon] = {
    if (!SystemTray.isSupported) {
      stage.setOnCloseRequest(_ => exit())
      return None
    }


    val tray: TrayIcon = new TrayIcon(ImageIO.read(getClass.getResource("/icon-small.png")), "Google Authenticator") {

      import java.awt._

      val popup = new PopupMenu()

      val menuFont = new Font("Segoe UI", java.awt.Font.PLAIN, 12)

      val showMenuItem = new MenuItem("Show")
      showMenuItem.setFont(menuFont.deriveFont(java.awt.Font.BOLD))
      showMenuItem.addActionListener(_ => Platform.runLater(stage.show()))

      val exitMenuItem = new MenuItem("Exit")
      exitMenuItem.setFont(menuFont)
      exitMenuItem.addActionListener(_ => Platform.runLater(exit()))

      exitMenuItem.setFont(menuFont)

      popup.add(showMenuItem)
      popup.add(exitMenuItem)

      this.setPopupMenu(popup)

      this.addActionListener(_ => Platform.runLater(
        if (stage.isShowing) stage.hide()
        else {
          stage.show()
          stage.requestFocus()
        }
      ))
    }



    SystemTray.getSystemTray.add(tray)


    Platform.implicitExit = false
    stage.setOnCloseRequest(_ => minimizeToTray())

    Some(tray)
  }


  def exit(): Unit = {
    stage.close()
    saveAccounts()
    Scheduler.awaitShutdownAll(1)
    System.exit(0)
  }

  def minimizeToTray(): Unit = stage.hide()


  object Mode extends Enumeration {

    case class ModeValue() extends Val {
      def !(mode2: Mode): Mode =
        if (this == Normal) mode2
        else Normal
    }

    type Mode = ModeValue

    val Deleting, Editing, Normal = ModeValue()
  }

}
