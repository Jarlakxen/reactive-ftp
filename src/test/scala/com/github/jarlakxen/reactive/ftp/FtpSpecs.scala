package com.github.jarlakxen.reactive.ftp

import scala.concurrent._
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.testkit._
import akka.util.ByteString

import org.junit.runner.RunWith
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.SpecificationLike
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

@RunWith(classOf[JUnitRunner])
class FtpSpecs(implicit ee: ExecutionEnv) extends TestKit(ActorSystem("FtpProtocolManagerSpec")) with DockerFTPSpec with ImplicitSender with SpecificationLike with AfterAll {
  import FtpSpecs._
  sequential

  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "Ftp" should {

    "list files by pattern" in {
      val files = Ftp().filesFrom("localhost", ftpPort, "test2", "test", "/", "^.*\\.txt$".r).runWith(sinkRemoteFileNames)

     files must be_==(List("file1.txt", "file2.txt")).awaitFor(5 seconds)
    }

    "download files by pattern" in {
      val filesContent = Ftp().filesFrom("localhost", ftpPort, "test2", "test", "/", "^.*\\.txt$".r).runWith(sinkRemoteFileContents).flatMap(contents => Future.sequence(contents))

      filesContent.map(_.map(_.utf8String)) must be_==(List("", "something")).awaitFor(5 seconds)
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