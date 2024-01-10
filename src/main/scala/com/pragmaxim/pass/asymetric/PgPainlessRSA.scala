package com.pragmaxim.pass.asymetric

import com.pragmaxim.pass
import com.pragmaxim.pass.*
import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.{PassPath, Password}
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.gpg.bc.internal.BouncyCastleGpgSigner
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import zio.{IO, Task, ZIO}

import java.io.*

case class PgPainlessRSA(keyRing: KeyRing, passwordReader: SecretReader) extends RSA with PGPainlessSupport:

  def gpgId: GpgId = keyRing.gpgId

  override def encrypt(passPath: PassPath, password: Password): ZIO[PassCtx, PgpError, Unit] =
    for
      publicKey <- keyRing.getPublicKeyOrFail
      _ <- ZIO
             .attempt(encr(publicKey, password, new FileOutputStream(passPath.toFile)))
             .mapError(er => PgpError(s"Unable to encrypt $passPath", er))
    yield ()

  override def decrypt(passPath: PassPath): ZIO[PassCtx, PgpError, Password] =
    for
      passphrase <- passwordReader.readPassphrase()
      privateKey <- keyRing.getPrivateKeyOrFail(passphrase)
      plaintext <- ZIO
                     .attempt(decr(privateKey, passphrase, new FileInputStream(passPath.toFile)))
                     .mapError(er => PgpError(s"Unable to decrypt $passPath", er))
    yield plaintext

object PgPainlessRSA extends PGPainlessSupport:
  def init(keyRing: KeyRing, secretReader: SecretReader): Task[PgPainlessRSA] =
    ZIO.attempt {
      init()
      PgPainlessRSA(keyRing, secretReader)
    }
