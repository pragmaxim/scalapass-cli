package com.pragmaxim.pass

import com.pragmaxim.pass.RSA.PgpType
import zio.mock.Expectation.value
import zio.test.*
import zio.test.Assertion.*
import zio.{Chunk, Exit, TaskLayer, ZIO, ZLayer}

import java.nio.file.Path as JPath
import scala.util.Random

object PassSpec extends ZIOSpecDefault with TestLayers:

  private def passTempDir = PassHomeDir.overriden(JPath.of(System.getProperty("java.io.tmpdir"), ".scalapass", Random.alphanumeric.take(10).mkString))

  private def tempDirLayer: TaskLayer[PassCtx & GitSession] = PassCtx.env(passTempDir, GpgExe()) >+> JGitSession.live

  def spec =
    suite("psw")(
      test("should fail if not initialized yet") {
        val exit = PassService.insert(false, JPath.of("test")).provide(tempDirLayer, SecretReaderMock.empty, ClipboardMock.empty, openLayer).exit
        assertZIO(exit)(fails(hasMessage(equalTo("Password store is not initialized"))))
      },
      test("should insert new password, show it and copy it") {
        val clipboardLayer = ClipboardMock.Copy(equalTo("test")).toLayer
        val secretReaderLayer =
          (SecretReaderMock.ReadPassPhrase(value("test")) andThen // signing git commit
            SecretReaderMock.ReadPassword(value("test")) andThen
            SecretReaderMock.ReadPassPhrase(value("test")).exactly(3) // encrypting > decrypting > decrypting
          ).toLayer
        (for
          _ <- PassService.insert(false, JPath.of("test"))
          x <- PassService.show(JPath.of("test"))
          y <- PassService.copy(JPath.of("test"))
        yield assertTrue(x == "test", y == "test")).provide(tempDirLayer, secretReaderLayer, clipboardLayer, initLayer(PgpType.bc))
      },
      test("should insert new passwords and list them as a tree") {
        val secretReaderLayer =
          (SecretReaderMock.ReadPassPhrase(value("test")) andThen // signing git commit
            SecretReaderMock.ReadPassword(value("bank-pass")) andThen
            SecretReaderMock.ReadPassPhrase(value("test")) andThen // encrypting
            SecretReaderMock.ReadPassword(value("web-pass")) andThen
            SecretReaderMock.ReadPassPhrase(value("test")) // encrypting
          ).toLayer
        val expectedTree    = Chunk("bank/", "  foo", "web/", "  bar")
        val expectedSubTree = Chunk("web/", "  bar")
        (for
          _       <- PassService.insert(false, JPath.of("bank/foo"))
          _       <- PassService.insert(false, JPath.of("web/bar"))
          tree    <- PassService.list(JPath.of("."))
          subTree <- PassService.list(JPath.of("web"))
        yield assertTrue(tree == expectedTree, subTree == expectedSubTree))
          .provide(tempDirLayer, secretReaderLayer, ClipboardMock.empty, initLayer(PgpType.bc))
      }
    )
