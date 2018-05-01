package me.maxih.googleauth.api

/**
  * Created by Maxi H. on 21.04.2018
  */
object TOTP {
  def unixTime: Long = System.currentTimeMillis() / 1000
}


class TOTP(secret: String, length: Int = 6) extends OTP(secret, length) {

  override def getAuthCode: Int = {
    super.setInput((TOTP.unixTime / 30).toInt)
    super.getAuthCode
  }

  def getRemaining: Int = 30 - (TOTP.unixTime - TOTP.unixTime / 30 * 30).toInt

}
