package com.github.jarlakxen.reactive.ftp

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.junit.runner.RunWith
import org.specs2.mutable.SpecificationLike
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.util.ByteString
import scala.concurrent.Future

@RunWith(classOf[JUnitRunner])
class FtpSpecs extends TestKit(ActorSystem("FtpProtocolManagerSpec")) with ImplicitSender with SpecificationLike with AfterAll {
  import FtpSpecs._
  sequential

  import system.dispatcher
  implicit val materializer = ActorMaterializer()
  
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Ftp" should {

    "list files by pattern" in new FTPContext("user", "password", FTPFile("File1.txt"), FTPFile("File2.txt", Some("something")), FTPFile("File2.csv", Some(",,,,,,")))(8124) {
      val files = Ftp().filesFrom("localhost", ftpPort, "user", "password", "/", "^.*\\.txt$".r).runWith(sinkRemoteFileNames)

      Await.result(files, 5 second) must be_==(List("File2.txt", "File1.txt"))
    }

    "download files by pattern" in new FTPContext("user", "password", FTPFile("File1.txt"), FTPFile("File2.txt", Some("something")), FTPFile("File2.csv", Some(",,,,,,")))(8124) {
      val filesContent = Ftp().filesFrom("localhost", ftpPort, "user", "password", "/", "^.*\\.txt$".r).runWith(sinkRemoteFileContents).flatMap(contents => Future.sequence(contents))

      Await.result(filesContent, 5 second).map(_.utf8String) must be_==(List("something", ""))
    }

  }

}

object FtpSpecs {

  val sinkRemoteFileNames =
    Flow[Ftp.RemoteFile]
      .map(_.name)
      .toMat(Sink.fold(List.empty[String])(_ :+ _))(Keep.right)

  def sinkRemoteFileContents(implicit materializer: ActorMaterializer) =
    Flow[Ftp.RemoteFile]
      .map(_.stream.runFold(ByteString.empty)(_ ++ _))
      .toMat(Sink.fold(List.empty[Future[ByteString]])(_ :+ _))(Keep.right)

}