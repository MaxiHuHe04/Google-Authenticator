package me.maxih.googleauth

import java.awt.{SystemTray, TrayIcon}
import java.io._
import java.util.prefs.Preferences

import javafx.collections.ListChangeListener
import javafx.scene.{Node => JFXNode}
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO
import me.maxih.googleauth.authentry.{AuthEntry, CounterAuthEntry, TimedAuthEntry}
import me.maxih.googleauth.util.SecretsHelper.HexBytes
import me.maxih.googleauth.util.Utils._
import me.maxih.googleauth.util.{Scheduler, SecretsHelper}
import org.apache.commons.codec.binary.Base32
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout._
import scalafx.scene.{Scene, control}
import scalafx.stage.FileChooser

import scala.collection.mutable

/**
  * Created by Maxi H. on 11.04.2018
  */
object GoogleAuthenticator extends JFXApp {
  val accounts = new ObservableBuffer[AuthEntry]()

  val mode: ObjectProperty[Mode.Mode] = new ObjectProperty[Mode.Mode](null, "", Mode.Normal)


  // Requires Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files
  SecretsHelper.extraSecure = parameters.unnamed.contains("--extra-secure") || parameters.unnamed.contains("-s")


  if (SecretsHelper.winDPAPI.isEmpty || parameters.unnamed.contains("--force-pass") || parameters.unnamed.contains("-p"))
    SecretsHelper.secretPassword = SecretsHelper.askPassword(4).getOrElse({
      System.exit(0)
      None
    })


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
              backupFileChooser.extensionFilters.add(new FileChooser.ExtensionFilter("Backup", "*.bak"))

              override def onClicked(event: MouseEvent): Unit = backupDialog.showAndWait() match {
                case Some(Save) =>
                  val file = backupFileChooser.showSaveDialog(stage)
                  if (file != null) {
                    val key = SecretsHelper.askPassword(6, "Please enter a PIN code to lock the backup.", emptyAllowed = true)
                    if (key.isDefined) backupAccounts(file, key.get)
                  }

                case Some(Load) =>
                  val file = backupFileChooser.showOpenDialog(stage)
                  if (file != null) {
                    val key = SecretsHelper.askPassword(6, "Please enter a PIN code to unlock the backup.", emptyAllowed = true)
                    if (key.isDefined) loadBackup(file, key.get)
                  }

                case _ =>
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

  loadAccounts(getAccounts)


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

  def backupAccounts(file: File, key: Option[SecretKeySpec]): Unit = {
    val out = new BufferedOutputStream(new FileOutputStream(file))
    out.write(if (key.isDefined) 1 else 0)
    out.write('\n')
    out.write(accounts.map(account => account.accountName.value + "\u0000" + SecretsHelper.encryptSecret(account.secret, if (account.isTimed) -1 else account.asInstanceOf[CounterAuthEntry].otp.counter, key, forceKey = true).toHexString).mkString("\n").getBytes())
    out.close()
  }

  def loadBackup(file: File, key: Option[SecretKeySpec]): Unit = {
    val out = new BufferedReader(new FileReader(file))
    val isEncrypted = out.read()
    out.read()
    val lines = out.lines()
    if (isEncrypted == 1 && key.isEmpty) {
      SecretsHelper.showWrongPassword(false)
      return
    }

    val backupAccounts = mutable.Map[String, (Int, String)]()

    lines.forEach(line => {
      val split = line.split("\u0000")
      if (split.length != 2) {
        showMalformedBackup()
        return
      }
      try backupAccounts(split(0)) = SecretsHelper.decryptSecret(SecretsHelper.hexStringToBytes(split(1)), if (isEncrypted == 1) key else None, {
        SecretsHelper.showWrongPassword(false); return
      }, forceKey = true)
      catch {
        case _: NumberFormatException =>
          showMalformedBackup()
          return
      }
    })


    loadAccounts(backupAccounts.toMap)
    saveAccounts()
    accounts.clear()
    loadAccounts(getAccounts)
  }

  def showMalformedBackup(): Unit = {
    val alert = new Alert(AlertType.Error, "The file you selected isn't a correct backup!", ButtonType.Close)
    alert.initOwner(stage)
    alert.headerText = "Malformed backup!"
    alert.show()
  }

  def loadAccounts(accounts: Map[String, (Int, String)]): Unit = this.accounts.appendAll(accounts.map({
    case (name, (count, secret)) if count >= 0 => CounterAuthEntry(name, secret, count)
    case (name, (_, secret)) => TimedAuthEntry(name, secret)
  }))

  private def getAccounts: Map[String, (Int, String)] = {
    val prefs = preferences
    val keys = prefs.get("accounts", ";;;;").split(";;;;")

    keys.iterator.map(k => k -> SecretsHelper.decryptSecret(prefs.getByteArray(k, Array()))).toMap
  }

  private def saveAccounts(): Unit = {
    val prefs = preferences

    prefs.clear()

    prefs.put("accounts", accounts.map(_.accountName.value).mkString(";;;;"))
    if (prefs.get("accounts", "") == "") prefs.remove("accounts")


    for (account <- accounts) {
      val currentCounter = if (account.isTimed) -1 else account.asInstanceOf[CounterAuthEntry].otp.counter

      prefs.putByteArray(account.accountName.value, SecretsHelper.encryptSecret(account.secret, currentCounter))
    }

  }

  private[googleauth] def preferences: Preferences = Preferences.userNodeForPackage(getClass)


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
