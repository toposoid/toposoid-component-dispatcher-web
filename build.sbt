import de.heikoseeberger.sbtheader.License
name := """toposoid-component-dispatcher-web"""
organization := "com.ideal.linked"

version := "0.6-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(AutomateHeaderPlugin)
scalaVersion := "2.13.11"

libraryDependencies += guice
libraryDependencies += "com.google.code.gson" % "gson" % "2.8.6"
libraryDependencies += "com.ideal.linked" %% "scala-common" % "0.6-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-common" % "0.6-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-knowledgebase-model" % "0.6-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-deduction-protocol-model" % "0.6-SNAPSHOT"
libraryDependencies += "javax.mail" % "mail" % "1.4.7"
libraryDependencies += "commons-io" % "commons-io" % "2.6"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.15"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.31"
libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
libraryDependencies +=  "com.ideal.linked" %% "toposoid-test-utils" % "0.6-SNAPSHOT" % Test
libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1" % Test

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("AGPL-3.0-or-later", new URL("http://www.gnu.org/licenses/agpl-3.0.en.html"))
headerLicense := Some(License.AGPLv3("2025", organizationName.value))
