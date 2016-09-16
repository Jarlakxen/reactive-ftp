package com.github.jarlakxen.reactive.ftp.client

import akka.actor._
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import com.github.jarlakxen.reactive.ftp.FtpClient

class FtpConnection(val addr: InetSocketAddress) extends FSM[FtpConnection.State, FtpConnection.Data] with ActorLogging {
  import FtpConnection._
  import context.system

  IO(Tcp) ! Tcp.Connect(addr)

  startWith(Connecting, Uninitialized)

  when(Connecting) {
    case Event(Tcp.Connected(remote, local), Uninitialized) => {
      sender ! Tcp.Register(self)
      goto(ReceivePlain) using PlainData(sender)
    }
  }

  when(ReceivePlain) {
    case Event(Tcp.Received(data), ctx: PlainData) => {
      data.utf8String match {
        case FtpClient.ResponsePattern(rawCode, rawMessage) => {
          log.debug(s"Received $rawCode $rawMessage")
          context.parent ! Response(rawCode.toInt, rawMessage)
          stay()
        }
        case FtpClient.MultilineResponsePattern(rawCode, rawMessage) => {
          log.debug(s"Received multiline $rawCode $rawMessage")
          goto(ReceiveMulti) using MultiData(ctx.connection, new StringBuffer(rawMessage))
        }
      }
    }
    case Event(Request(line), ctx: PlainData) => {
      log.debug(s"Sending $line")
      ctx.connection ! Tcp.Write(ByteString(s"$line\r\n"))
      stay()
    }
    case Event(Tcp.PeerClosed, ctx: PlainData) => {
      context.parent ! FtpClient.Disconnected
      self ! PoisonPill
      stay()
    }
  }

  when(ReceiveMulti) {
    case Event(Tcp.Received(data), ctx: MultiData) => {
      data.utf8String match {
        case FtpClient.ResponsePattern(rawCode, rawMessage) => {
          log.debug(s"Received EOL $rawCode $rawMessage")
          val bytes = rawMessage.getBytes
          ctx.data.append(rawMessage)
          context.parent ! Response(rawCode.toInt, ctx.data.toString)
          goto(ReceivePlain) using PlainData(ctx.connection)
        }
        case rawMessage: String => {
          ctx.data.append(rawMessage)
          stay()
        }
      }
    }
  }
}

object FtpConnection {

  case class Request(line: String)
  case class Response(code: Int, line: String)

  trait Data
  case object Uninitialized extends Data
  case class PlainData(connection: ActorRef) extends Data
  case class MultiData(connection: ActorRef, data: StringBuffer) extends Data

  trait State
  case object Connecting extends State
  case object ReceivePlain extends State
  case object ReceiveMulti extends State
}