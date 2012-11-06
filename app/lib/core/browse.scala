package org.scalajars.core

import scalaz._, Scalaz._

trait Browser {
  this: Store =>

  def projects = listProjects()

  def search(term: String) = searchProjects(term)

  def project(name: String) = getProject(name)

  def index(path: Path): Error \/ (Option[Path], List[IndexItem]) = getIndex(path).map { items => (path.baseOption, items) }

  object dependencies {
    def sbt(version: Version, scalaVersion: ScalaVersion, artifact: Artifact) =
      """libraryDependencies += "%s" %%%% "%s" %% "%s" """.format(artifact.groupId, artifact.id, version.id)
  }

}

