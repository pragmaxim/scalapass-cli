package com.pragmaxim.pass.asymetric

import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.{PassPath, Password}
import com.pragmaxim.pass.{PassCtx, PgpError, RSA}
import zio.ZIO

import scala.sys.process.*

case class GnuPgRSA(gpgId: GpgId) extends RSA:

  override def encrypt(passPath: PassPath, password: Password): ZIO[PassCtx, PgpError, Unit] =
    for
      ctx <- ZIO.service[PassCtx]
      echoCommand  = s"echo -n $password"
      gnuPgCommand = s"${ctx.gpgExe} --encrypt --recipient $gpgId -a --output $passPath"
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
