package com.pragmaxim.pass

import com.pragmaxim.pass.KeyRing.{GpgId, SecretValue}
import com.pragmaxim.pass.RSA.{PassPhrase, PgpType}
import zio.{RLayer, ZIO, ZLayer}

import scala.io.Source

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

  val openLayer: RLayer[PassCtx & GitSession & SecretReader & Clipboard, KeyRing & RSA & PassService] =
    keyRingLayer >+> RSA.open >+> PassService.layer

  def initLayer(pgpType: PgpType): RLayer[PassCtx & GitSession & SecretReader & Clipboard, KeyRing & RSA & PassService] =
    keyRingLayer >+> RSA.init(pgpType) >+> PassService.layer

}
