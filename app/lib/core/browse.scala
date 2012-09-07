package org.scalajars.core

import scalaz._, Scalaz._


trait Browser {
  this: Store =>

  def projects = listProjects()
}
