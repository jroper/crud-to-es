organization in ThisBuild := "com.example"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.11.8"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test

lagomCassandraEnabled in ThisBuild := false
// Disabled so that it can be externally stopped/started for the purpose of demoing
lagomKafkaEnabled in ThisBuild := false

lazy val `holiday-listing` = (project in file("."))
  .aggregate(`reservation-api`, `reservation-impl`, `search-api`, `search-impl`)

lazy val `reservation-api` = (project in file("reservation-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `reservation-impl` = (project in file("reservation-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      "com.typesafe.play" %% "play-slick" % "2.0.2",
      "com.h2database" % "h2" % "1.4.196",
      lagomScaladslKafkaBroker,
      lagomScaladslPubSub,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`reservation-api`, `search-api`)

lazy val `search-api` = (project in file("search-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  ).dependsOn(`reservation-api`)

lazy val `search-impl` = (project in file("search-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslTestKit,
      lagomScaladslKafkaClient,
      macwire,
      scalaTest
    )
  )
  .dependsOn(`search-api`, `reservation-api`)

lazy val `web-gateway` = (project in file("web-gateway"))
  .enablePlugins(PlayScala && LagomPlay)
  .disablePlugins(PlayLayoutPlugin)
  .dependsOn(`reservation-api`, `search-api`)
  .settings(
    version := "1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      lagomScaladslServer,
      macwire,
      scalaTest,
      "org.webjars" % "foundation" % "6.2.3",
      "org.webjars" % "foundation-icon-fonts" % "d596a3cfb3"
    ),
    lagomWatchDirectories ++= (sourceDirectories in (Compile, TwirlKeys.compileTemplates)).value
  )

commands += Command.command("startKafka") { state =>
  IO.delete(Project.extract(state).get(baseDirectory) / "target" / "lagom-dynamic-projects" / "lagom-internal-meta-project-kafka")
  if (Project.extract(state).get(lagomKafkaEnabled)) {
    "lagomKafkaStart" :: state
  } else {
    "set lagomKafkaEnabled in ThisBuild := true" :: "lagomKafkaStart" :: state
  }
}
addCommandAlias("stopKafka", "lagomKafkaStop")