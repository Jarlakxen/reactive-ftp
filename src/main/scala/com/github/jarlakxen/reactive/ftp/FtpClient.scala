package com.github.jarlakxen.reactive.ftp

import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.scaladsl.Source
import akka.util.ByteString

object FtpClient {

  def apply()(implicit system: ActorSystem): ActorRef = system.actorOf(Props(classOf[client.FtpProtocolManager]))
  
  trait ConnectionMode
  case object ActiveMode extends ConnectionMode
  case object PassiveMode extends ConnectionMode

  case class Connect(host: String, port: Int, authentication: Option[Authentication])
  object Connect {
    def apply(host: String): Connect = apply(host, DefaultFtpPort)
    def apply(host: String, port: Int): Connect = Connect(host, port, None)
    def apply(host: String, username: String, password: String): Connect = apply(host, DefaultFtpPort, username, password)
    def apply(host: String, port: Int, username: String, password: String): Connect = Connect(host, port, Some(Authentication(username, password)))
  }
  case object Connected
  case object ConnectionFailed

  case class Authentication(username: String, password: String)
  case object AuthenticationSuccess
  case object AuthenticationFail

  case class Download(path: String)
  case class DownloadInProgress(stream: Source[ByteString, NotUsed])
  case object DownloadSuccess
  case class DownloadFail(reason: Throwable)

  case object Disconnect
  case object Disconnected

  case class Dir(path: String)
  case class DirListing(files: List[EntryInfo])
  case object DirFail

  
  trait EntryInfo {
      def name: String
      def size: Long
      def user: String
      def group: String
      def mode: String
  }
  case class FileInfo(name: String, size: Long, user: String, group: String, mode: String) extends EntryInfo
  case class DirInfo(name: String, size: Long, user: String, group: String, mode: String) extends EntryInfo

  val ResponsePattern = "(\\d+) (.*)\r\n".r
  val MultilineResponsePattern = "(\\d+)\\-(.*)\r?\n?.*\r?\n?".r
  val ListPattern = "([drwsx\\-]+)\\s+(\\d+)\\s+(\\w+)\\s+(\\w+)\\s+(\\d+)\\s+(\\w{3})\\s+(\\d+)\\s+([\\d:]+)\\s+([\\w\\.]+)\r?".r
  val DefaultFtpPort = 21

}