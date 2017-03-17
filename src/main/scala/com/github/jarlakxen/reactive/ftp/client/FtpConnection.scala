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
      extractMessage(data.utf8String.lines.toList).foreach{
        case (code, message) => context.parent ! Response(code, message)
      }
      stay()
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
}

object FtpConnection {
  import FtpClient._
  
  private[FtpConnection] def extractMessage(dataLines: List[String]): List[(Int, String)] = {

    def extractMultiLineData(accMsg: String, multiLineData: List[String]): (String, List[String]) =
      multiLineData match {
        case MultilineResponsePattern(_, msg) :: tail => extractMultiLineData(accMsg + "\n" + msg, tail)
        case SingleLineResponsePattern(code, msg) :: tail => (accMsg + "\n" + msg, tail)
        case Nil => (accMsg, Nil)
      }
    
    dataLines match {
      case SingleLineResponsePattern(code, msg) :: tail => 
        (code.toInt, msg) :: extractMessage(tail)
        
      case MultilineResponsePattern(code, msg) :: tail => 
        val (multiLineMessage, rest) = extractMultiLineData(msg, tail)
        (code.toInt, multiLineMessage) :: extractMessage(rest)
     
      case Nil => Nil
    }
  }

  case class Request(line: String)
  case class Response(code: Int, line: String)

  trait Data
  case object Uninitialized extends Data
  case class PlainData(connection: ActorRef) extends Data

  trait State
  case object Connecting extends State
  case object ReceivePlain extends State
  case object ReceiveMulti extends State
}