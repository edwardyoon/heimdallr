scalaVersion := "2.11.8"
val akkaVersion = "2.5.16"
val akkaHTTPVersion = "10.1.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHTTPVersion,

  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.4",
  "com.typesafe.scala-logging" % "scala-logging-slf4j_2.11" % "2.1.2",
  "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.1",
  "net.debasishg" %% "redisclient" % "3.8"
)
