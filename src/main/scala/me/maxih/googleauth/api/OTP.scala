package me.maxih.googleauth.api

import java.nio.ByteBuffer

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base32


/**
  * Created by Maxi H. on 20.04.2018
  */
object OTP {

  private def hmac(key: Array[Byte], message: Array[Byte], algorithm: String = "HmacSHA1"): Array[Byte] = {
    if (key.isEmpty) throw new IllegalArgumentException("Empty key!")
    val signingKey = new SecretKeySpec(key, algorithm)

    val mac = Mac.getInstance(algorithm)
    mac.init(signingKey)

    mac.doFinal(message)
  }

}


class OTP(secret: String, length: Int = 6) {
  require(length <= 8 && length >= 4)

  private var input = 0

  def setInput(input: Int): OTP = {
    this.input = input
    this
  }

  def getAuthCode: Int = {
    val key = new Base32().decode(secret)

    val buffer = ByteBuffer.allocate(8)
    buffer.putLong(0, input)

    val hash = OTP.hmac(key, buffer.array())
    val offset = hash.last & 0x0F

    for (i <- 0 to 4) buffer.put(i, hash(i + offset))

    val code = buffer.getInt(0) & 0x7FFFFFFF

    code % Math.pow(10, length).toInt
  }

}