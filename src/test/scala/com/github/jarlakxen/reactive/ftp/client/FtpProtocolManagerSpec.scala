package com.github.jarlakxen.reactive.ftp.client

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.junit.runner.RunWith
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.SpecificationLike
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

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

import com.github.jarlakxen.reactive.ftp._

@RunWith(classOf[JUnitRunner])
class FtpProtocolManagerSpec(implicit ee: ExecutionEnv) extends TestKit(ActorSystem("FtpProtocolManagerSpec")) with DockerFTPSpec with ImplicitSender with SpecificationLike with AfterAll {
  import FtpProtocolManagerSpec._
  import FtpClient._
  sequential

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "FtpProtocolManager" >> {

    "connect and disconnect" in {
      val client = FtpClient()

      client ! FtpClient.Connect("localhost", ftpPort, "test1", "test")

      expectMsg(FtpClient.Connected)
      expectMsg(FtpClient.AuthenticationSuccess)

      client ! FtpClient.Disconnect

      expectMsg(FtpClient.Disconnected)
      
      ok
    }

    "connect, list and disconnect" in {
      val client = FtpClient()

      client ! FtpClient.Connect("localhost", ftpPort, "test1", "test")

      expectMsg(FtpClient.Connected)
      expectMsg(FtpClient.AuthenticationSuccess)

      client ! FtpClient.Dir("/")

      expectMsg(DirListing(List(FileInfo("file1.txt", 0, "1000", "ftpgroup", "rw-r--r--"), FileInfo("file2.txt", 10, "1000", "ftpgroup", "rw-r--r--"), DirInfo("somedir", 4096, "1000", "ftpgroup", "rwxr-xr-x"))))

      client ! FtpClient.Disconnect

      expectMsg(FtpClient.Disconnected)
      
      ok
    }

    "connect, download and disconnect" in {
      val client = FtpClient()

      client ! FtpClient.Connect("localhost", ftpPort, "test1", "test")

      expectMsg(FtpClient.Connected)
      expectMsg(FtpClient.AuthenticationSuccess)

      client ! FtpClient.Download("/file1.txt")

      expectMsgPF(hint = """Expecting FtpClient.DownloadInProgress("")""") {
        case FtpClient.DownloadInProgress(stream) =>
          val content = Await.result(stream.runWith(sinkUnderTest), 100 millis)
          if (content.length == 0) ok else failure
      }

      expectMsg(FtpClient.DownloadSuccess)

      client ! FtpClient.Download("/file2.txt")

      expectMsgPF(hint = """Expecting FtpClient.DownloadInProgress("something")""") {
        case FtpClient.DownloadInProgress(stream) =>
          val content = Await.result(stream.runWith(sinkUnderTest), 100 millis).utf8String
          if (content == "something\n") ok else failure
      }

      expectMsg(FtpClient.DownloadSuccess)

      client ! FtpClient.Disconnect

      expectMsg(FtpClient.Disconnected)
      
      ok
    }
  }

}

object FtpProtocolManagerSpec {

  val sinkUnderTest = Flow[ByteString].toMat(Sink.fold(ByteString.empty)(_ ++ _))(Keep.right)

}