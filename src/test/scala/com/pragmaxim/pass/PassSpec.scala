package com.pragmaxim.pass

import com.pragmaxim.pass.GitLike.GitType
import com.pragmaxim.pass.RSA.PgpType
import zio.mock.Expectation.value
import zio.test.*
import zio.test.Assertion.*
import zio.{Chunk, Exit, ZIO, ZLayer}

import java.nio.file.Path as JPath

object PassSpec extends ZIOSpecDefault with TestLayers:

  def spec =
    suite("psw")(
      test("should fail if not initialized yet") {
        val exit = PassService
          .insert(false, JPath.of("test"))
          .provide(SecretReaderMock.empty, ClipboardMock.empty, baseLayer, GitLike.open, RSA.open, PassService.layer)
          .exit
        assertZIO(exit)(fails(hasMessage(equalTo("Password store is not initialized"))))
      },
      test("should insert new password, show it and copy it") {
        val clipboardLayer = ClipboardMock.Copy(equalTo("test")).toLayer
        val secretReaderLayer =
          (SecretReaderMock.ReadPassword(value("test")) andThen
            SecretReaderMock.ReadPassPhrase(value("test")).exactly(3) // encrypting > decrypting > decrypting
          ).toLayer
        (for
          _ <- PassService.insert(false, JPath.of("test"))
          x <- PassService.show(JPath.of("test"))
          y <- PassService.copy(JPath.of("test"))
        yield assertTrue(x == "test", y == "test"))
          .provide(secretReaderLayer, clipboardLayer, baseLayer, GitLike.init(GitType.jgit), RSA.init(PgpType.bc), PassService.layer)
      },
      test("should insert new passwords and list them as a tree") {
        val secretReaderLayer =
          (SecretReaderMock.ReadPassword(value("bank-pass")) andThen
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
          .provide(secretReaderLayer, ClipboardMock.empty, baseLayer, GitLike.init(GitType.jgit), RSA.init(PgpType.bc), PassService.layer)
      }
    )
