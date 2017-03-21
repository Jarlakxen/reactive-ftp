import sbt.Keys._

// ··· Project Info ···

name := "reactive-ftp"

organization := "com.github.jarlakxen"

crossScalaVersions := Seq("2.11.8", "2.12.1")

scalaVersion := "2.11.8" // crossScalaVersions.value.head

fork in run  := true

publishMavenStyle := true

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))


// ··· Project Options ···

scalacOptions ++= Seq(
    "-encoding",
    "utf8",
    "-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-unchecked",
    "-deprecation"
)

scalacOptions in Test ++= Seq("-Yrangepos")

parallelExecution in Test := false

// ··· Project Dependancies ···

val akkaV           = "2.4.17"
val slf4JV          = "1.7.21"
val logbackV        = "1.2.2"
val dockerTestKitV  = "0.9.1"
val spec2V          = "3.8.9"
val jUnitV          = "4.12"

libraryDependencies ++= Seq(
  // --- Akka --
  "com.typesafe.akka"             %% "akka-slf4j"                         % akkaV           % "provided",
  "com.typesafe.akka"             %% "akka-stream"                        % akkaV           % "provided",
  // --- Testing ---
  "ch.qos.logback"                %  "logback-classic"                    % logbackV        % "test",
  "com.whisk"                     %% "docker-testkit-specs2"              % dockerTestKitV  % "test",
  "com.whisk"                     %% "docker-testkit-impl-spotify"        % dockerTestKitV  % "test",
  "com.spotify"                   %  "docker-client"                      % "8.2.0"         % "test",
  "com.typesafe.akka"             %% "akka-stream-testkit"                % akkaV           % "test",
  "org.specs2"                    %% "specs2-core"                        % spec2V          % "test",
  "org.specs2"                    %% "specs2-junit"                       % spec2V          % "test"
)