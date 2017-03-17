package com.github.jarlakxen.reactive

import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.FileSystemEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import org.specs2.mutable._

import com.whisk.docker._
import com.whisk.docker.specs2._
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.spotify.docker.client.{ DefaultDockerClient, DockerClient }
import org.apache.commons.net.ftp._
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.specs2.specification.AfterAll

/**
 * @author fviale
 */
package object ftp {

  sealed trait FTPEntry {
    def isFile: Boolean
    def isDir: Boolean = !isFile
  }

  case class FTPFile(name: String, content: Option[String] = None) extends FTPEntry { def isFile: Boolean = true }
  case class FTPDir(name: String, childrens: FTPEntry*) extends FTPEntry { def isFile: Boolean = false }

  abstract class FTPContext(user: String, password: String, filesystem: FTPEntry*)(implicit val ftpPort: Int = 8123) extends BeforeAfter {
    var fakeFtpServer: FakeFtpServer = _

    override def before: Unit = {
      fakeFtpServer = new FakeFtpServer()
      fakeFtpServer.setServerControlPort(ftpPort)
      fakeFtpServer.addUserAccount(new UserAccount(user, password, "/"));

      val fakeFS = new UnixFakeFileSystem()

      def toFakeFS(basePath: String, entries: List[FTPEntry]): List[FileSystemEntry] = entries.flatMap {
        case FTPFile(name, Some(content)) => List(new FileEntry(basePath + name, content))
        case FTPFile(name, None) => List(new FileEntry(basePath + name))
        case FTPDir(name) => List(new DirectoryEntry(basePath + name))
        case FTPDir(name, childrens @ _*) => new DirectoryEntry(basePath + name) :: toFakeFS(basePath + name + "/", childrens.toList)
      }

      fakeFS.add(new DirectoryEntry("/"))
      toFakeFS("/", filesystem.toList).foreach(fakeFS.add(_))

      fakeFtpServer.setFileSystem(fakeFS)
      fakeFtpServer.start
    }

    override def after: Unit = {
      fakeFtpServer.stop
    }
  }

  trait DockerFTPSpec extends DockerTestKit {

    val defaultName = "ftpd_server"

    implicit val ftpPort = 21

    val pasvPortRange = (30000 to 30009).toList

    private val resourcesPath = this.getClass().getClassLoader().getResource(".").getPath()

    private val client: DockerClient = DefaultDockerClient.fromEnv().build()

    override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

    val ftpContainer = DockerContainer("stilliard/pure-ftpd:latest", Some(defaultName))
      .withHostname("localhost")
      .withPorts((ftpPort -> Some(21) :: pasvPortRange.map(p => p -> Some(p))): _*)
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