package com.github.jarlakxen.reactive

import org.specs2.mutable._

import com.whisk.docker._
import com.whisk.docker.specs2._
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.spotify.docker.client.{ DefaultDockerClient, DockerClient }
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.specs2.specification.AfterAll
import scala.util.Random

/**
 * @author fviale
 */
package object ftp {

  trait DockerFTPSpec extends DockerTestKit {

    val defaultName = "ftpd-server-" + Random.nextInt

    implicit val ftpPort = 2021

    val pasvPortRange = (30000 to 30009).toList

    private val resourcesPath = this.getClass().getClassLoader().getResource(".").getPath()

    private val client: DockerClient = DefaultDockerClient.fromEnv().build()

    override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

    val ftpContainer = DockerContainer("stilliard/pure-ftpd:latest", Some(defaultName))
      .withHostname("localhost")
      .withPorts((21 -> Some(ftpPort) :: pasvPortRange.map(p => p -> Some(p))): _*)
      .withEnv("PUBLICHOST=localhost")
      .withVolumes(Seq(
        VolumeMapping(resourcesPath + "ftpusers", "/home/ftpusers", true),
        VolumeMapping(resourcesPath + "passwd", "/etc/pure-ftpd/passwd", true),
        VolumeMapping(resourcesPath + "db", "/etc/pure-ftpd/db", true)))
      .withReadyChecker(DockerReadyChecker.Always)

    abstract override def dockerContainers: List[DockerContainer] =
      ftpContainer :: super.dockerContainers
  }

}