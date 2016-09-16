import sbt.Keys._

// ··· Project Info ···

name := "reactive-ftp"

organization := "com.github.jarlakxen"

crossScalaVersions := Seq("2.11.8")

scalaVersion <<= (crossScalaVersions) { versions => versions.head }

fork in run  := true

publishMavenStyle := true

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

// ··· Project Enviroment ···


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

// ··· Project Dependancies ···

val akkaV           = "2.4.10"
val mockFtpServerV  = "2.6"
val slf4JV          = "1.7.21"
val logbackV        = "1.1.7"
val spec2V          = "3.8.5"
val jUnitV          = "4.12"

libraryDependencies ++= Seq(
  // --- Akka --
  "com.typesafe.akka"             %% "akka-slf4j"                         % akkaV           % "provided",
  "com.typesafe.akka"             %% "akka-stream"                        % akkaV           % "provided",
  // --- Testing ---
  "ch.qos.logback"                %  "logback-classic"                    % logbackV        % "test",
  "org.mockftpserver"             %  "MockFtpServer"                      % mockFtpServerV  % "test",
  "com.typesafe.akka"             %% "akka-stream-testkit"                % akkaV           % "test",
  "org.specs2"                    %% "specs2-core"                        % spec2V          % "test",
  "org.specs2"                    %% "specs2-mock"                        % spec2V          % "test",
  "org.specs2"                    %% "specs2-junit"                       % spec2V          % "test"
)