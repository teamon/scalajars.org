package org.scalajars.web

import org.scalajars.core._

package object controllers {

  trait RedisStore extends RedisStoreImpl {
    def namespace = "scalajars"
  }

  object Browser extends Browser with RedisStore
  object Publisher extends Publisher with RedisStore
  object Users extends Users with RedisStore

}
