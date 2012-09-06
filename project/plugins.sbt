// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"

addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "0.7.2")

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.0.3")
