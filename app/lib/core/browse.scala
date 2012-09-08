package org.scalajars.core

import scalaz._, Scalaz._


trait Browser {
  this: Store =>

  def projects = listProjects()

  def project(name: String) = getProject(name)
}

object PrintDependency {
  def sbt(version: Version, scalaVersion: ScalaVersion, artifact: Artifact) =
    """libraryDependencies += "%s" %%%% "%s" %% "%s" """.format(artifact.groupId, artifact.id, version.id)
}
