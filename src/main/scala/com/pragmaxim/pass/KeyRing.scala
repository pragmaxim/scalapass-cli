package com.pragmaxim.pass

import com.pragmaxim.pass.KeyRing.*
import com.pragmaxim.pass.RSA.PassPhrase
import com.pragmaxim.pass.asymetric.BouncyCastleSupport
import zio.{TaskLayer, ULayer, ZIO, ZLayer}

import scala.sys.process.*

trait KeyRing:
  def gpgId: GpgId

  def getPrivateKeyOrFail(passphrase: PassPhrase): ZIO[PassCtx, PgpError, SecretValue]

  def getPublicKeyOrFail: ZIO[PassCtx, PgpError, SecretValue]

object KeyRing:
  type SecretValue = String
  type GpgId       = String

case class GnuPGKeyRing(gpgId: GpgId) extends KeyRing with BouncyCastleSupport:

  def getPrivateKeyOrFail(passphrase: PassPhrase): ZIO[PassCtx, PgpError, SecretValue] =
    for
      ctx <- ZIO.service[PassCtx]
      gnuPgCommand = s"${ctx.gpgExe} --export-secret-key --batch --pinentry-mode loopback --passphrase $passphrase -a $gpgId"
      secret <- ZIO.attempt(gnuPgCommand.!!).mapError(er => PgpError(s"Getting private key for $gpgId from GnuPG failed", er))
      secretOpt = Option(secret).map(_.trim).filter(_.nonEmpty)
      result <- ZIO.fromOption(secretOpt).orElseFail(PgpError(s"No private key for $gpgId found"))
    yield result

  def getPublicKeyOrFail: ZIO[PassCtx, PgpError, SecretValue] =
    for
      ctx <- ZIO.service[PassCtx]
      gnuPgCommand = s"${ctx.gpgExe} --export -a $gpgId"
      secret <- ZIO.attempt(gnuPgCommand.!!).mapError(er => PgpError(s"Getting public key for $gpgId from GnuPG failed", er))
      secretOpt = Option(secret).map(_.trim).filter(_.nonEmpty)
      result <- ZIO.fromOption(secretOpt).orElseFail(PgpError(s"No public key for $gpgId found"))
    yield result

object GnuPGKeyRing:

  def init(gpgId: GpgId): ULayer[KeyRing] = ZLayer.succeed(GnuPGKeyRing(gpgId))

  def open: ZLayer[PassCtx, PassError, KeyRing] =
    ZLayer {
      for
        ctx <- ZIO.service[PassCtx]
        gpgIdPath = ctx.passDir.gpgIdPath
        gpgId <- ZIO.readFile(gpgIdPath).mapError(er => UserError(s"Password store is not initialized", er))
      yield GnuPGKeyRing(gpgId)
    }
