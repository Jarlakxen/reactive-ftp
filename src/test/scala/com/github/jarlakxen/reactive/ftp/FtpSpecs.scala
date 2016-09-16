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


@RunWith(classOf[JUnitRunner])
class FtpSpecs extends TestKit(ActorSystem("FtpProtocolManagerSpec")) with ImplicitSender with SpecificationLike with AfterAll {
  import FtpSpecs._
  sequential

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Ftp" should {

    "download files by pattern" in new FTPContext("user", "password", FTPFile("File1.txt"), FTPFile("File2.txt", Some("something")), FTPFile("File2.csv", Some(",,,,,,")))(8124) {
      val files = Ftp().filesFrom("localhost", ftpPort, "user", "password", "/", "\\*.txt".r).runWith(sinkRemoteFileNames)

      Await.result(files, 5 second) must be_==(Nil)
    }

  }

}

object FtpSpecs {

  val sinkRemoteFileNames = Flow[Ftp.RemoteFile].map(_.name).toMat(Sink.fold(List.empty[String])(_ :+ _))(Keep.right)

}