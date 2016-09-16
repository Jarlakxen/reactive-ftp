package com.github.jarlakxen.reactive.ftp

import scala.util.matching.Regex

import com.github.jarlakxen.reactive.ftp.FtpClient.Authentication
import com.github.jarlakxen.reactive.ftp.FtpClient.FileInfo
import com.github.jarlakxen.reactive.ftp.client.FtpProtocolManager

import akka.NotUsed
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Stash
import akka.actor.actorRef2Scala
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage
import akka.stream.scaladsl.Source
import akka.util.ByteString

class FtpFilesPublisher(
    host: String,
    port: Int = FtpClient.DefaultFtpPort,
    authentication: Option[Authentication] = None,
    basePath: String,
    filePattern: Regex) extends ActorPublisher[Ftp.RemoteFile] with Stash with ActorLogging {

  val client = context.system.actorOf(Props[FtpProtocolManager])

  override def preStart(): Unit = {
    client ! FtpClient.Connect(host, port, authentication)
  }

  override def receive = awaitConnect

  def awaitConnect: Receive = {
    case FtpClient.Connected if authentication.isDefined =>
      context.become(awaitAuthentication)
    case FtpClient.Connected =>
      listDirectory
    case FtpClient.ConnectionFailed =>
      onErrorThenStop(new RuntimeException("Could not connect to target FTP."))
    case _ =>
      stash()
  }

  def awaitAuthentication: Receive = {
    case FtpClient.AuthenticationSuccess =>
      listDirectory
    case FtpClient.AuthenticationFail =>
      onErrorThenStop(new RuntimeException("Authentication fail."))
    case _ =>
      stash()
  }

  def awaitListOfFiles: Receive = {
    case FtpClient.DirListing(entries) =>
      val files = entries.collect { case entry: FileInfo if filePattern.pattern.matcher(entry.name).matches() => entry }
      unstashAll()
      log.debug(s"Files to process: ${files.map(_.name).mkString("; ")}.")
      context.become(ready(files))
    case FtpClient.DirFail =>
      onErrorThenStop(new RuntimeException(s"Cannot list target directory $basePath."))
    case _ =>
      stash()
  }

  def ready(files: List[FileInfo]): Receive = {
    case ActorPublisherMessage.Request(requestedElements) =>
      log.debug(s"Requesting $requestedElements elements")
      val (filesToGet, pendingFiles) = files.splitAt(requestedElements.toInt)
      askForContent(filesToGet)
      if (pendingFiles.nonEmpty)
        context.become(ready(pendingFiles))
      else
        onCompleteThenStop()

    case ActorPublisherMessage.Cancel | ActorPublisherMessage.SubscriptionTimeoutExceeded =>
      log.debug("Canceling streaming")
      client ! FtpClient.Disconnect
      onCompleteThenStop()
  }

  def askForContent(filesToGet: List[FileInfo]): Unit =
    filesToGet.foreach { file =>
      val content: Source[ByteString, _] = Source.actorPublisher[Source[ByteString, NotUsed]](Props(classOf[FtpFileDownloadPublisher], client, basePath, file)).flatMapConcat(identity)
      onNext(Ftp.RemoteFile(file.name, file.size, file.user, file.group, file.mode, content))
    }

  def listDirectory: Unit = {
    client ! FtpClient.Dir(basePath)
    context.become(awaitListOfFiles)
  }
}

class FtpFileDownloadPublisher(client: ActorRef, basePath: String, file: FileInfo) extends ActorPublisher[Source[ByteString, NotUsed]] with ActorLogging {

  override def receive: Receive = {
    case ActorPublisherMessage.Request(_) =>
      log.debug(s"Initializing download for ${file.name}.")
      client ! FtpClient.Download((if (basePath endsWith "/") basePath else basePath + "/") + file.name)
      context.become(awaitForContent)
  }

  def awaitForContent: Receive = {
    case FtpClient.DownloadInProgress(stream) =>
      log.debug(s"Download for ${file.name} is in progress.")
      onNext(stream)
      context.become(awaitForComplete)

    case FtpClient.DownloadFail =>
      onErrorThenStop(new RuntimeException(s"Cannot download file ${file.name} in $basePath."))
  }

  def awaitForComplete: Receive = {
    case FtpClient.DownloadSuccess =>
      log.debug(s"Finished download fro ${file.name}.")
      onCompleteThenStop()

    case FtpClient.DownloadFail =>
      onErrorThenStop(new RuntimeException(s"Cannot download file ${file.name} in $basePath."))
  }
}