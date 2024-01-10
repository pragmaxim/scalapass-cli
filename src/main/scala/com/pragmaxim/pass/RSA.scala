package com.pragmaxim.pass

import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.{PassPath, Password}
import com.pragmaxim.pass.asymetric.{BouncyCastleRSA, GnuPgRSA, PgPainlessRSA}
import zio.{IO, Task, UIO, ZIO, ZLayer}

import java.nio.file.Path as JPath

trait RSA:
  def gpgId: GpgId
  def encrypt(passPath: PassPath, password: Password): ZIO[PassCtx, PgpError, Unit]
  def decrypt(passPath: PassPath): ZIO[PassCtx, PgpError, Password]
  def status: UIO[String] = ZIO.succeed(s"${getClass.getSimpleName} with gpg-id $gpgId")

object RSA:
  type Password   = String
  type PassPhrase = String
  type PassPath   = JPath

  enum PgpType(val value: String):
    case bc extends PgpType("bc")
    case gnupg extends PgpType("gnupg")
    case pgpainless extends PgpType("pgpainless")

  private def chooseRSA(keyRing: KeyRing, pgpType: PgpType, secretReader: SecretReader): Task[RSA] =
    pgpType match
      case PgpType.bc =>
        ZIO.succeed(BouncyCastleRSA(keyRing, secretReader))
      case PgpType.gnupg =>
        ZIO.succeed(GnuPgRSA(keyRing.gpgId))
      case PgpType.pgpainless =>
        PgPainlessRSA.init(keyRing, secretReader)

  def init(pgpType: PgpType): ZLayer[PassCtx & KeyRing & GitLike & SecretReader, PassError, RSA] =
    ZLayer {
      for
        ctx     <- ZIO.service[PassCtx]
        keyRing <- ZIO.service[KeyRing]
        pr      <- ZIO.service[SecretReader]
        _       <- IOUtils.writeToFile(ctx.passDir.pgpTypePath, pgpType.value)
        _       <- IOUtils.writeToFile(ctx.passDir.gpgIdPath, keyRing.gpgId)
        rsa     <- chooseRSA(keyRing, pgpType, pr).mapError(er => SystemError(s"RSA initialization failed", er))
      yield rsa
    }

  def open: ZLayer[PassCtx & KeyRing & GitLike & SecretReader, PassError, RSA] =
    ZLayer {
      for
        ctx     <- ZIO.service[PassCtx]
        keyRing <- ZIO.service[KeyRing]
        pr      <- ZIO.service[SecretReader]
        pgpTypePath = ctx.passDir.pgpTypePath
        pgpType <- ZIO.readFile(pgpTypePath).mapError(er => UserError(s"Password store is not initialized", er))
        rsa     <- chooseRSA(keyRing, PgpType.valueOf(pgpType), pr).mapError(er => SystemError(s"RSA initialization failed", er))
      yield rsa
    }
