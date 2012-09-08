package org.scalajars.core

object DigestUtils {
  import java.security.SecureRandom
  import java.math.BigInteger

  private val random = new SecureRandom()

  def newRandomToken() = new BigInteger(130, random).toString(32)
}
