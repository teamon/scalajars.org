package org.scalajars.core

import scalaz._, Scalaz._

trait Users {
  this: Store =>

  def saveUser(user: User) = {
    setUser(user)
    getUserToken(user).map(_.toRightDisjunction(TokenNotFound)).join ||| setUserToken(user, UserToken.random)
  }

  def resetUserToken(user: User) = setUserToken(user, UserToken.random)
}
