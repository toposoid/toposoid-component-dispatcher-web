import de.heikoseeberger.sbtheader.License
name := """toposoid-component-dispatcher-web"""
organization := "com.ideal.linked"

version := "0.7-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(AutomateHeaderPlugin)
scalaVersion := "3.3.6"
val PekkoVersion = "1.1.5"
libraryDependencies += guice
libraryDependencies += "com.google.code.gson" % "gson" % "2.8.6" exclude("org.slf4j","slf4j-api")
libraryDependencies += "com.ideal.linked" %% "scala-common" % "0.7-SNAPSHOT" exclude("org.slf4j","slf4j-api")
libraryDependencies += "com.ideal.linked" %% "toposoid-common" % "0.7-SNAPSHOT" exclude("org.slf4j","slf4j-api")
libraryDependencies += "com.ideal.linked" %% "toposoid-knowledgebase-model" % "0.7-SNAPSHOT" exclude("org.slf4j","slf4j-api")
libraryDependencies += "com.ideal.linked" %% "toposoid-deduction-protocol-model" % "0.7-SNAPSHOT" exclude("org.slf4j","slf4j-api")
libraryDependencies += "javax.mail" % "mail" % "1.4.7" exclude("org.slf4j","slf4j-api")
libraryDependencies += "commons-io" % "commons-io" % "2.6" exclude("org.slf4j","slf4j-api")
libraryDependencies += "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion exclude("org.slf4j","slf4j-api")
libraryDependencies += "org.apache.pekko" %% "pekko-serialization-jackson" % PekkoVersion exclude("org.slf4j","slf4j-api")
libraryDependencies += "org.apache.pekko" %% "pekko-slf4j" % PekkoVersion exclude("org.slf4j","slf4j-api")
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test exclude("org.slf4j","slf4j-api")
libraryDependencies += "com.ideal.linked" %% "toposoid-test-utils" % "0.7-SNAPSHOT" % Test exclude("org.slf4j","slf4j-api")
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.36" 

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("AGPL-3.0-or-later", url("http://www.gnu.org/licenses/agpl-3.0.en.html"))
headerLicense := Some(License.AGPLv3("2025", organizationName.value))
