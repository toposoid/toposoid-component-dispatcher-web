name := """toposoid-component-dispatcher-web"""
organization := "com.ideal.linked"

version := "0.5-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(AutomateHeaderPlugin)

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))


scalaVersion := "2.12.12"

libraryDependencies += guice
libraryDependencies += "com.google.code.gson" % "gson" % "2.8.6"
libraryDependencies += "com.ideal.linked" %% "scala-common" % "0.5-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-common" % "0.5-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-knowledgebase-model" % "0.5-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-deduction-protocol-model" % "0.5-SNAPSHOT"
libraryDependencies += "javax.mail" % "mail" % "1.4.7"
libraryDependencies += "commons-io" % "commons-io" % "2.6"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.14"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.31"
libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies += "com.ideal.linked" %% "toposoid-sentence-transformer-neo4j" % "0.5-SNAPSHOT" % Test
libraryDependencies += "com.ideal.linked" %% "toposoid-feature-vectorizer" % "0.5-SNAPSHOT" % Test
libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1" % Test
