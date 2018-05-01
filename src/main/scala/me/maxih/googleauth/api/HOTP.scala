package me.maxih.googleauth.api

/**
  * Created by Maxi H. on 21.04.2018
  */
object HOTP {
}

class HOTP(secret: String, length: Int = 6, start: Int = 0) extends OTP(secret, length) {
  var counter: Int = start

  override def getAuthCode: Int = {
    super.setInput(counter)
    super.getAuthCode
  }

  def next(): Unit = counter += 1

}
