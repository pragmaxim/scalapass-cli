package com.pragmaxim.pass.asymetric

import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.{PassPath, Password}
import com.pragmaxim.pass.{PgpError, PassCtx, RSA}
import org.eclipse.jgit.api.CommitCommand
import zio.Console.printLine
import zio.{IO, ZIO}

import scala.sys.process.*

case class GnuPgRSA(gpgId: GpgId) extends RSA:
  override def sign(cmd: CommitCommand): IO[PgpError, CommitCommand] =
    ZIO.attempt(cmd.setSigningKey(gpgId)).mapError(er => PgpError(s"Unable to sign git commit", er))

  override def encrypt(passPath: PassPath, password: Password): ZIO[PassCtx, PgpError, Unit] =
    for
      ctx <- ZIO.service[PassCtx]
      echoCommand  = s"echo -n $password"
      gnuPgCommand = s"${ctx.gpgExe} --encrypt --recipient $gpgId -a --output $passPath"
      _ <- printLine(gnuPgCommand).ignore
      _ <- ZIO
             .attempt((echoCommand #| gnuPgCommand).!!.trim)
             .mapError(er => PgpError(s"Encrypting password for $gpgId failed", er))
    yield ()

  override def decrypt(passPath: PassPath): ZIO[PassCtx, PgpError, Password] =
    for
      ctx <- ZIO.service[PassCtx]
      gnuPgCommand = s"${ctx.gpgExe} -q --decrypt --recipient $gpgId $passPath"
      password <- ZIO
                    .attempt(gnuPgCommand.!!)
                    .mapError(er => PgpError(s"Decrypting password for $gpgId failed", er))
    yield password
