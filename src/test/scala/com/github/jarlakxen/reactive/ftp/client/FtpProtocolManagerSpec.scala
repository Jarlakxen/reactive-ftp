package com.github.jarlakxen.reactive.ftp.client

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.junit.runner.RunWith
import org.specs2.mutable.SpecificationLike
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

import com.github.jarlakxen.reactive.ftp.FTPContext
import com.github.jarlakxen.reactive.ftp.FTPDir
import com.github.jarlakxen.reactive.ftp.FTPFile
import com.github.jarlakxen.reactive.ftp.FtpClient

import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.util.ByteString

@RunWith(classOf[JUnitRunner])
class FtpProtocolManagerSpec extends TestKit(ActorSystem("FtpProtocolManagerSpec")) with ImplicitSender with SpecificationLike with AfterAll {
  import FtpProtocolManagerSpec._
  sequential

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "FtpProtocolManager" >> {
    "connect and disconnect" in new FTPContext("user", "password") {
      val client = system.actorOf(Props[FtpProtocolManager])

      client ! FtpClient.Connect("localhost", ftpPort, "user", "password")

      expectMsg(FtpClient.Connected)
      expectMsg(FtpClient.AuthenticationSuccess)

      client ! FtpClient.Disconnect

      expectMsg(FtpClient.Disconnected)
    }

    "connect, list and disconnect" in new FTPContext("user", "password", FTPFile("File1.txt"), FTPFile("File2.txt", Some("something")), FTPDir("somedir")) {
      val client = system.actorOf(Props[FtpProtocolManager])

      client ! FtpClient.Connect("localhost", ftpPort, "user", "password")

      expectMsg(FtpClient.Connected)
      expectMsg(FtpClient.AuthenticationSuccess)

      client ! FtpClient.Dir("/")

      expectMsg(FtpClient.DirListing(List(FtpClient.FileInfo("File2.txt", 9, "none", "none", "rwxrwxrwx"), FtpClient.DirInfo("somedir", 0, "none", "none", "rwxrwxrwx"), FtpClient.FileInfo("File1.txt", 0, "none", "none", "rwxrwxrwx"))))

      client ! FtpClient.Disconnect

      expectMsg(FtpClient.Disconnected)
    }

    "connect, download and disconnect" in new FTPContext("user", "password", FTPFile("File1.txt"), FTPFile("File2.txt", Some("something"))) {
      val client = system.actorOf(Props[FtpProtocolManager])

      client ! FtpClient.Connect("localhost", ftpPort, "user", "password")

      expectMsg(FtpClient.Connected)
      expectMsg(FtpClient.AuthenticationSuccess)

      client ! FtpClient.Download("/File1.txt")

      expectMsgPF(hint = """Expecting FtpClient.DownloadInProgress("")""") {
        case FtpClient.DownloadInProgress(stream) =>
          val content = Await.result(stream.runWith(sinkUnderTest), 100 millis)
          if (content.length == 0) ok else failure
      }

      expectMsg(FtpClient.DownloadSuccess)

      client ! FtpClient.Download("/File2.txt")

      expectMsgPF(hint = """Expecting FtpClient.DownloadInProgress("something")""") {
        case FtpClient.DownloadInProgress(stream) =>
          val content = Await.result(stream.runWith(sinkUnderTest), 100 millis).utf8String
          if (content == "something") ok else failure
      }

      expectMsg(FtpClient.DownloadSuccess)

      client ! FtpClient.Disconnect

      expectMsg(FtpClient.Disconnected)
    }

  }

}

object FtpProtocolManagerSpec {

  val sinkUnderTest = Flow[ByteString].toMat(Sink.fold(ByteString.empty)(_ ++ _))(Keep.right)

}