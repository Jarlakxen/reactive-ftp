package com.github.jarlakxen.reactive.ftp

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import scala.util.matching.Regex
import akka.actor.Props
import akka.actor.ActorRef
import akka.util.ByteString
import akka.NotUsed

final class Ftp(system: ActorSystem) {

  def filesFrom(host: String,
    port: Int,
    basePath: String,
    filePattern: Regex): Source[Ftp.RemoteFile, ActorRef] =
    Source.actorPublisher(Props(classOf[FtpFilesPublisher], host, port, None, basePath, filePattern))

  def filesFrom(host: String,
    port: Int,
    usarname: String,
    password: String,
    basePath: String,
    filePattern: Regex): Source[Ftp.RemoteFile, ActorRef] =
    Source.actorPublisher(Props(classOf[FtpFilesPublisher], host, port, Some(FtpClient.Authentication(usarname, password)), basePath, filePattern))

}

object Ftp {

  def apply()(implicit system: ActorSystem): Ftp = new Ftp(system)
  
  case class RemoteFile(name: String, size: Long, user: String, group: String, mode: String, stream: Source[ByteString, _])
}
