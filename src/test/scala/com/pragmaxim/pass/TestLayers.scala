package com.pragmaxim.pass

import com.pragmaxim.pass.KeyRing.{GpgId, SecretValue}
import com.pragmaxim.pass.RSA.PassPhrase
import zio.{ZIO, ZLayer}

import java.nio.file.Path as JPath
import scala.io.Source
import scala.util.Random

trait TestLayers {

  val keyRingLayer = ZLayer.succeed(
    new KeyRing {
      def gpgId: GpgId = "psw-scala@unknown.com"

      def getPrivateKeyOrFail(passphrase: PassPhrase): ZIO[PassCtx, PgpError, SecretValue] =
        ZIO
          .attempt(
            Source
              .fromInputStream(Thread.currentThread().getContextClassLoader.getResourceAsStream("secret.gpg"))
              .getLines()
              .mkString("\n")
          )
          .mapError(er => PgpError("Unable to read secret.gpg", er))

      def getPublicKeyOrFail: ZIO[PassCtx, PgpError, SecretValue] =
        ZIO
          .attempt(
            Source
              .fromInputStream(Thread.currentThread().getContextClassLoader.getResourceAsStream("public.gpg"))
              .getLines()
              .mkString("\n")
          )
          .mapError(er => PgpError("Unable to read public.gpg", er))
    }
  )

  private def passTempDir = PassHomeDir.overriden(JPath.of(System.getProperty("java.io.tmpdir"), ".scalapass", Random.alphanumeric.take(10).mkString))

  def baseLayer: ZLayer[SecretReader & Clipboard, PassError, PassCtx & KeyRing] =
    PassCtx.env(passTempDir, GpgExe(), GitExe()) >+> keyRingLayer

}
