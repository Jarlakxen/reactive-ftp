# Reactive FTP

This is a pure Scala fully non-blocking and asynchronous FTP client, implemented from scratch using Akka.

The current implementation use actors and streams, but for v2.0.0 the client is going to completely rely on akka-streams.

## Usage

In your `build.sbt`, add the following entries:

```scala
resolvers += Resolver.bintrayRepo("jarlakxen", "maven")

libraryDependencies += "com.github.jarlakxen" %% "reactive-ftp" % "1.0.0"
```


## Supported operations

- [x] Connect / Disconnect
- [x] Authentication
- [x] List directory
- [x] Download file
- [ ] Upload file
- [ ] Other things ¿?

## Examples:

Take a look at the [specs](https://github.com/Jarlakxen/reactive-ftp/blob/master/src/test/scala/com/github/jarlakxen/reactive/ftp/client/FtpProtocolManagerSpec.scala).

### List directory
```scala
implicit val system = ActorSystem();

val client = FtpClient()

client ! FtpClient.Connect("localhost", ftpPort, "user", "password")

expectMsg(FtpClient.Connected)
expectMsg(FtpClient.AuthenticationSuccess)

client ! FtpClient.Dir("/")

expectMsg(FtpClient.DirListing(List(FtpClient.FileInfo("File2.txt", 9, "none", "none", "rwxrwxrwx"), FtpClient.DirInfo("somedir", 0, "none", "none", "rwxrwxrwx"), FtpClient.FileInfo("File1.txt", 0, "none", "none", "rwxrwxrwx"))))

client ! FtpClient.Disconnect

expectMsg(FtpClient.Disconnected)
```

### Download File
```scala
implicit val system = ActorSystem();

val client = FtpClient()

client ! FtpClient.Connect("localhost", ftpPort, "user", "password")

expectMsg(FtpClient.Connected)
expectMsg(FtpClient.AuthenticationSuccess)

client ! FtpClient.Download("/File1.txt")

expectMsgPF() {
    case FtpClient.DownloadInProgress(stream) =>
        val content = Await.result(stream.runWith(sinkUnderTest), 100 millis)
        if (content.length == 0) ok else failure
}

expectMsg(FtpClient.DownloadSuccess)

client ! FtpClient.Download("/File2.txt")

expectMsgPF() {
    case FtpClient.DownloadInProgress(stream) =>
        val content = Await.result(stream.runWith(sinkUnderTest), 100 millis).utf8String
        if (content == "something") ok else failure
}

expectMsg(FtpClient.DownloadSuccess)

client ! FtpClient.Disconnect

expectMsg(FtpClient.Disconnected)
```

### Stream files

```scala
// Get all the *.csv from "/files_to_process" folder.
// This is how RemoteFile looks like: case class RemoteFile(name: String, size: Long, user: String, group: String, mode: String, stream: Source[ByteString, _])
// where stream is the content of the file
val source: Source[Ftp.RemoteFile, _] = Ftp().filesFrom("localhost", 8081, "user", "password", "/files_to_process", "^.*\\.csv$".r)

// Get the content of every file in the stream and return them as a list
def sinkRemoteFileContents(implicit materializer: ActorMaterializer): Future[List[Future[ByteString]]] =
Flow[Ftp.RemoteFile]
  .map(_.stream.runFold(ByteString.empty)(_ ++ _))
  .toMat(Sink.fold(List.empty[Future[ByteString]])(_ :+ _))(Keep.right)

val filesContent: Future[List[ByteString]] = source.runWith(sinkRemoteFileContents).flatMap(Future.sequence(_))
```