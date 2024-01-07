package com.pragmaxim.pass

import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.{PassPath, Password}
import com.pragmaxim.pass.asymetric.{BouncyCastleRSA, GnuPgRSA, PgPainlessRSA}
import org.eclipse.jgit.api.CommitCommand
import zio.{IO, Task, UIO, ZIO, ZLayer}

import java.nio.file.Path as JPath

trait RSA:
  def gpgId: GpgId
  def encrypt(passPath: PassPath, password: Password): ZIO[PassCtx, PgpError, Unit]
  def decrypt(passPath: PassPath): ZIO[PassCtx, PgpError, Password]
  def sign(cmd: CommitCommand): IO[PgpError, CommitCommand]
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

  def init(pgpType: PgpType): ZLayer[PassCtx & KeyRing & GitSession & SecretReader, PassError, RSA] =
    ZLayer {
      for
        ctx     <- ZIO.service[PassCtx]
        git     <- ZIO.service[GitSession]
        keyRing <- ZIO.service[KeyRing]
        pr      <- ZIO.service[SecretReader]
        pgpTypePath = ctx.passDir.pgpTypePath
        gpgIdPath   = ctx.passDir.gpgIdPath
        _   <- ZIO.attempt(ctx.passDir.toFile.mkdirs()).mapError(er => SystemError(s"Unable to create ${ctx.passDir}", er))
        _   <- ZIO.writeFile(gpgIdPath, keyRing.gpgId).mapError(er => SystemError(s"Writing ${keyRing.gpgId} to $gpgIdPath failed", er))
        _   <- ZIO.writeFile(pgpTypePath, pgpType.value).mapError(er => SystemError(s"Writing $pgpType to $pgpTypePath failed", er))
        rsa <- chooseRSA(keyRing, pgpType, pr).mapError(er => SystemError(s"RSA initialization failed", er))
        _   <- git.commitFileAndSign(s"Adding gpg-id and pgp-type", ctx.passDir.pgpTypePathRelative, ctx.passDir.gpgIdPathRelative)(rsa.sign)
      yield rsa
    }

  def open: ZLayer[PassCtx & KeyRing & GitSession & SecretReader, PassError, RSA] =
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
