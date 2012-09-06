package play.redisclient

import play.api._
import com.redis._


class RedisPlugin(app: Application) extends Plugin {
  private lazy val host     = app.configuration.getString("redis.host").getOrElse("localhost")
  private lazy val port     = app.configuration.getInt("redis.port").getOrElse(6379)
  // private lazy val timeout  = app.configuration.getInt("redis.timeout").getOrElse(2000)
  // private lazy val password = app.configuration.getString("redis.password")

  // override lazy val enabled = {
    // !app.configuration.getString("redisplugin").filter(_ == "disabled").isDefined
  // }

  lazy val pool = new RedisClientPool(host, port)
}
