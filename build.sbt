Global / onChangedBuildSource := ReloadOnSourceChanges
Global / excludeLintKeys += scalacOptions

// All Twitter library releases are date versioned as YY.MM.patch
val releaseVersion = "24.8.0-SNAPSHOT"

val logbackVersion = "1.2.11"
val opencensusVersion = "0.19.1"
val slf4jVersion = "1.7.30"

def util(which: String) = "com.twitter" %% ("util-" + which) % releaseVersion
def finagle(which: String) = "com.twitter" %% ("finagle-" + which) % releaseVersion

lazy val noPublishSettings = Seq(
  publish / skip := true
)

def gcJavaOptions: Seq[String] = {
  Seq(
    "-XX:+UseG1GC",
    "-XX:ReservedCodeCacheSize=128m",
    "-Xss8M",
    "-Xms512M",
    "-Xmx2G"
  )
}

def travisTestJavaOptions: Seq[String] = {
  // We have some custom configuration for the Travis environment
  // https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
  val travisBuild = sys.env.getOrElse("TRAVIS", "false").toBoolean
  if (travisBuild) {
    Seq(
      "-DSKIP_FLAKY=true",
      "-DSKIP_FLAKY_TRAVIS=true"
    )
  } else {
    Seq(
      "-DSKIP_FLAKY=true"
    )
  }
}

lazy val sharedSettings = Seq(
  version := releaseVersion,
  organization := "com.twitter",
  scalaVersion := "2.13.18",
  crossScalaVersions := Seq("2.13.18"),
  Test / fork := true, // We have to fork to get the JavaOptions
  libraryDependencies ++= Seq(
    // See https://www.scala-sbt.org/0.13/docs/Testing.html#JUnit
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.mockito" % "mockito-all" % "1.9.5" % "test",
    "org.scalacheck" %% "scalacheck" % "1.15.4" % "test",
    "org.scalatest" %% "scalatest" % "3.1.2" % "test",
    "org.scalatestplus" %% "junit-4-12" % "3.1.2.0" % "test",
    "org.scalatestplus" %% "mockito-1-10" % "3.1.0.0" % "test",
    "org.scalatestplus" %% "scalacheck-1-14" % "3.1.2.0" % "test"
  ),
  ivyXML :=
    <dependencies>
      <exclude org="com.sun.jmx" module="jmxri" />
      <exclude org="com.sun.jdmk" module="jmxtools" />
      <exclude org="javax.jms" module="jms" />
    </dependencies>,
  scalacOptions ++= Seq(
    "-release:21",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint",
    "-encoding",
    "utf8"
  ),
  javacOptions ++= Seq("-Xlint:unchecked", "-source", "21", "-target", "21"),
  doc / javacOptions := Seq("-source", "21"),
  javaOptions ++= Seq(
    "-Djava.net.preferIPv4Stack=true",
    "-server"
  ),
  javaOptions ++= gcJavaOptions,
  Test / javaOptions ++= travisTestJavaOptions,
  Test / javaOptions ++= Seq(
    "--add-opens=java.base/java.lang=ALL-UNNAMED"
  ),
  // This is bad news for things like com.twitter.util.Time
  Test / parallelExecution := false,
  // -a: print stack traces for failing asserts
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-a"),
  // Sonatype publishing
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  publishMavenStyle := true,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  autoAPIMappings := true,
  apiURL := Some(url("https://twitter.github.io/twitter-server/docs/")),
  pomExtra :=
    <url>https://github.com/twitter/twitter-server</url>
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>https://www.apache.org/licenses/LICENSE-2.0</url>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:twitter/twitter-server.git</url>
      <connection>scm:git:git@github.com:twitter/twitter-server.git</connection>
    </scm>
    <developers>
      <developer>
        <id>twitter</id>
        <name>Twitter Inc.</name>
        <url>https://www.twitter.com/</url>
      </developer>
    </developers>,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
)

lazy val root = (project in file("."))
  .enablePlugins(
    ScalaUnidocPlugin
  )
  .settings(sharedSettings)
  .settings(noPublishSettings)
  .aggregate(
    twitterServer,
    twitterServerOpenCensus,
    twitterServerSlf4jJdk14,
    twitterServerSlf4jLog4j12,
    twitterServerSlf4jLogbackClassic)

lazy val twitterServer = (project in file("server"))
  .enablePlugins(
    ScalaUnidocPlugin
  )
  .settings(name := "twitter-server", moduleName := "twitter-server", sharedSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      finagle("core"),
      finagle("http"),
      finagle("stats-core"),
      finagle("toggle"),
      finagle("tunable"),
      util("app"),
      util("core"),
      util("jackson"),
      util("jvm"),
      util("lint"),
      util("registry"),
      util("slf4j-api"),
      util("slf4j-jul-bridge"),
      util("tunable")
    )
  )

lazy val twitterServerOpenCensus = (project in file("opencensus"))
  .enablePlugins(
    ScalaUnidocPlugin
  )
  .settings(
    name := "twitter-server-opencensus",
    moduleName := "twitter-server-opencensus",
    sharedSettings)
  .settings(
    libraryDependencies ++= Seq(
      finagle("core"),
      finagle("http"),
      "io.opencensus" % "opencensus-api" % opencensusVersion,
      "io.opencensus" % "opencensus-contrib-zpages" % opencensusVersion
    ))
  .dependsOn(twitterServer)

lazy val twitterServerSlf4jJdk14 = (project in file("slf4j-jdk14"))
  .settings(
    name := "twitter-server-slf4j-jdk14",
    moduleName := "twitter-server-slf4j-jdk14",
    sharedSettings)
  .settings(libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.slf4j" % "slf4j-jdk14" % slf4jVersion,
    "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
    "org.slf4j" % "log4j-over-slf4j" % slf4jVersion
  ))
  .dependsOn(twitterServer)

lazy val twitterServerSlf4jLog4j12 = (project in file("slf4j-log4j12"))
  .settings(
    name := "twitter-server-slf4j-log4j12",
    moduleName := "twitter-server-slf4j-log4j12",
    sharedSettings)
  .settings(libraryDependencies ++= Seq(
    "log4j" % "log4j" % "1.2.17" % "provided",
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.slf4j" % "slf4j-log4j12" % slf4jVersion,
    "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
    "org.slf4j" % "jul-to-slf4j" % slf4jVersion
  ))
  .dependsOn(twitterServer)

lazy val twitterServerSlf4jLogbackClassic = (project in file("logback-classic"))
  .settings(
    name := "twitter-server-logback-classic",
    moduleName := "twitter-server-logback-classic",
    sharedSettings)
  .settings(libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % logbackVersion % "provided",
    "ch.qos.logback" % "logback-core" % logbackVersion % "provided",
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
    "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
    "org.slf4j" % "log4j-over-slf4j" % slf4jVersion
  ))
  .dependsOn(twitterServer)

lazy val twitterServerDoc = (project in file("doc"))
  .enablePlugins(
    ScalaUnidocPlugin,
    SphinxPlugin
  )
  .settings(
    name := "twitter-server-doc",
    sharedSettings,
    Seq(
      doc / scalacOptions ++= Seq("-doc-title", "TwitterServer", "-doc-version", version.value),
      Sphinx / includeFilter := ("*.html" | "*.png" | "*.js" | "*.css" | "*.gif" | "*.txt")
    )
  )
  .configs(DocTest).settings(inConfig(DocTest)(Defaults.testSettings): _*)
  .settings(
    DocTest / unmanagedSourceDirectories += baseDirectory.value / "src/sphinx/code",
    // Make the "test" command run both, test and doctest:test
    test := Seq(Test / test, DocTest / test).dependOn.value
  )
  .dependsOn(twitterServer)

/* Test Configuration for running tests on doc sources */
lazy val DocTest = config("testdoc") extend Test
