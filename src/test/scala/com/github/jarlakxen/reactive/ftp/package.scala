package com.github.jarlakxen.reactive

import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.FileSystemEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import org.specs2.mutable.BeforeAfter

/**
 * @author fviale
 */
package object ftp {

  sealed trait FTPEntry

  case class FTPFile(name: String, content: Option[String] = None) extends FTPEntry
  case class FTPDir(name: String, childrens: FTPEntry*) extends FTPEntry

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

}