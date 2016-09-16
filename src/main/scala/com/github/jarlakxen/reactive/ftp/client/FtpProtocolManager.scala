package com.github.jarlakxen.reactive.ftp.client

import akka.actor.{ Props, ActorRef, FSM, Actor, ActorLogging, PoisonPill, Stash }
import java.net.InetSocketAddress
import akka.util.ByteString
import scala.concurrent.duration._
import com.github.jarlakxen.reactive.ftp.FtpClient
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import scala.util._

class FtpProtocolManager extends FSM[FtpProtocolManager.State, FtpProtocolManager.Data] with Stash {
  import FtpProtocolManager._

  implicit val system = context.system
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(FtpClient.Connect(host, port, auth), Uninitialized) => {
      context.actorOf(Props(classOf[FtpConnection], new InetSocketAddress(host, port)), name = "ftp_connection")
      stay() using Initializing(sender, auth)
    }
    case Event(FtpConnection.Response(code, message), Initializing(commander, authentication)) if code == 220 => {
      log.debug(s"Greet - Code:$code Message:$message")
      commander ! FtpClient.Connected
      authentication match {
        case Some(auth) =>
          self ! auth
          goto(Connected) using AuthenticationContext(commander, sender, auth)
        case None =>
          goto(Active) using ConnectionContext(sender)
      }
    }
  }

  when(Connected) {
    case Event(FtpClient.Authentication(login, password), ctx: AuthenticationContext) => {
      ctx.connection ! FtpConnection.Request(s"USER $login")
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: AuthenticationContext) if code == 331 => {
      ctx.connection ! FtpConnection.Request(s"PASS ${ctx.authentication.password}")
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: AuthenticationContext) if code == 230 => {
      log.debug(s"Authentication success.")
      ctx.commander ! FtpClient.AuthenticationSuccess
      goto(Active) using ConnectionContext(ctx.connection)
    }
  }

  when(Active) {
    case Event(FtpClient.Dir(path), ctx: ConnectionContext) => {
      log.debug(s"List directory $path.")
      self ! StartListing
      goto(Listing) using TransferContext(ctx.connection, path, None, sender)
    }
    case Event(FtpClient.Download(path), ctx: ConnectionContext) => {
      log.debug(s"Download file $path.")
      self ! StartDownloading
      goto(Downloading) using TransferContext(ctx.connection, path, None, sender)
    }
    case Event(FtpClient.Disconnect, ctx: ConnectionContext) => {
      log.debug(s"Disconnect.")
      self ! FtpClient.Disconnect
      goto(Disconnecting) using DisconnectContext(ctx.connection, sender)
    }
  }

  when(Downloading) {
    case Event(StartDownloading, ctx: TransferContext) => {
      ctx.connection ! FtpConnection.Request("TYPE I")
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: TransferContext) if code == 200 => {
      ctx.connection ! FtpConnection.Request("PASV")
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: TransferContext) if code == 227 => {
      val address = extractAddress(message)

      address.foreach { addr =>
        ctx.connection ! FtpConnection.Request(s"RETR ${ctx.path}")
        log.debug(s"Connecting to ${addr.getHostString}:${addr.getPort} for transfer data.")
      }

      stay() using ctx.copy(dataAddr = address)
    }
    case Event(FtpConnection.Response(code, message), TransferContext(_, path, Some(addr), replayTo)) if code == 150 => {
      log.debug("Sending incoming transfer file to parent.")
      replayTo ! FtpClient.DownloadInProgress(Source.repeat(ByteString.empty).via(Tcp().outgoingConnection(addr)))
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: TransferContext) if code == 226 => {
      log.debug("Closing data connection.")
      ctx.replayTo ! FtpClient.DownloadSuccess
      unstashAll()
      goto(Active) using ConnectionContext(ctx.connection)
    }
    case Event(_, _) => {
      stash()
      stay()
    }
  }

  when(Disconnecting) {
    case Event(FtpClient.Disconnect, ctx: DisconnectContext) => {
      ctx.connection ! FtpConnection.Request("QUIT")
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: DisconnectContext) if code == 221=> {
      ctx.replayTo ! FtpClient.Disconnected
      goto(Idle) using Uninitialized
    }
  }

  when(Listing) {
    case Event(StartListing, ctx: TransferContext) => {
      ctx.connection ! FtpConnection.Request("TYPE I")
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: TransferContext) if code == 200 => {
      ctx.connection ! FtpConnection.Request(s"CWD ${ctx.path}")
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: TransferContext) if code == 250 => {
      ctx.connection ! FtpConnection.Request("PASV")
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: TransferContext) if code == 227 => {
      val address = extractAddress(message)

      address.foreach { addr =>
        ctx.connection ! FtpConnection.Request("LIST")
        log.debug(s"Connecting to $addr for transfer data.")
      }

      stay() using ctx.copy(dataAddr = address)
    }
    case Event(FtpConnection.Response(code, message), TransferContext(_, path, Some(addr), _)) if code == 150 => {
      Source.repeat(ByteString.empty).via(Tcp().outgoingConnection(addr)).toMat(Sink.fold(ByteString.empty)(_ ++ _))(Keep.right).run().andThen {
        case Success(data) =>
          log.debug("Sending incoming transfer file to parent.")
          self ! TransferBytes(data)
        case Failure(ex) =>
          log.error(ex, s"Fail to transfer data when listing directory '$path'")
      }
      stay()
    }
    case Event(FtpConnection.Response(code, message), ctx: TransferContext) if code == 226 => {
      log.debug("Closing data connection.")
      stay()
    }
    case Event(TransferBytes(data), ctx: TransferContext) => {
      val lines = data.utf8String.split("\n").toList
      val files = lines.collect {
        case FtpClient.ListPattern(mode, inodes, user, group, size, month, day, timeOrYear, name) if mode startsWith "-" =>
          FtpClient.FileInfo(name, size.toLong, user, group, mode.substring(1))
        case FtpClient.ListPattern(mode, inodes, user, group, size, month, day, timeOrYear, name) if mode startsWith "d" =>
          FtpClient.DirInfo(name, size.toLong, user, group, mode.substring(1))
      }
      log.debug(s"Listing directory ${ctx.path}: ${files.mkString}")
      ctx.replayTo ! FtpClient.DirListing(files)
      unstashAll()
      goto(Active) using ConnectionContext(ctx.connection)
    }
    case Event(_, _) => {
      stash()
      stay()
    }
  }

  def extractAddress(message: String): Option[InetSocketAddress] = message match {
    case ADDRESS_PATTERN(host1, host2, host3, host4, port1, port2) => {
      val host = s"$host1.$host2.$host3.$host4"
      val port = (port1.toInt << 8) + port2.toInt
      Some(new InetSocketAddress(host, port))
    }
    case _ =>
      log.debug(s"Invalid address pattern in message $message")
      None
  }

}

object FtpProtocolManager {

  val ADDRESS_PATTERN = ".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*".r

  case object StartListing
  case object StartDownloading

  trait Data
  case object Uninitialized extends Data
  case class Initializing(commander: ActorRef, authentication: Option[FtpClient.Authentication]) extends Data
  case class AuthenticationContext(commander: ActorRef, connection: ActorRef, authentication: FtpClient.Authentication) extends Data
  case class ConnectionContext(connection: ActorRef) extends Data
  case class TransferContext(connection: ActorRef, path: String, dataAddr: Option[InetSocketAddress] = None, replayTo: ActorRef) extends Data
  case class DisconnectContext(connection: ActorRef, replayTo: ActorRef) extends Data
  case class TransferBytes(data: ByteString)

  trait State
  case object Idle extends State
  case object Connected extends State
  case object Active extends State
  case object Listing extends State
  case object Downloading extends State
  case object Disconnecting extends State
}