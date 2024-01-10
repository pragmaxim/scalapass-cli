package com.pragmaxim.pass.asymetric

import com.pragmaxim.pass.*
import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.{PassPath, PassPhrase, Password}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.{PGPPrivateKey, PGPPublicKey}
import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.gpg.bc.internal.BouncyCastleGpgSigner
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import zio.{IO, ZIO}

import java.io.*
import java.security.Security

case class BouncyCastleRSA(keyRing: KeyRing, passwordReader: SecretReader) extends RSA with BouncyCastleSupport {

  override def gpgId: GpgId = keyRing.gpgId

  private def buildPublicKeyOrFail: ZIO[PassCtx, PgpError, PGPPublicKey] =
    for
      pk           <- keyRing.getPublicKeyOrFail
      publicKeyOpt <- ZIO.attempt(buildPublicKey(pk)).mapError(er => PgpError(s"Unable to build public key for $gpgId", er))
      publicKey    <- ZIO.fromOption(publicKeyOpt).orElseFail(PgpError(s"No public key for $gpgId found"))
    yield publicKey

  override def encrypt(passPath: PassPath, password: Password): ZIO[PassCtx, PgpError, Unit] =
    for
      publicKey <- buildPublicKeyOrFail
      _         <- ZIO.attempt(encrypt(publicKey, passPath, password)).mapError(e => PgpError(s"Unable to encrypt data", e))
    yield ()

  private def buildPrivateKeyOrFail(passphrase: PassPhrase, keyId: Long): ZIO[PassCtx, PgpError, PGPPrivateKey] =
    for
      pk            <- keyRing.getPrivateKeyOrFail(passphrase)
      privateKeyOpt <- ZIO.attempt(buildPrivateKey(pk, passphrase, keyId)).mapError(er => PgpError(s"Building private key for $gpgId failed", er))
      privateKey    <- ZIO.fromOption(privateKeyOpt).orElseFail(PgpError(s"No private key for $gpgId found"))
    yield privateKey

  override def decrypt(passPath: PassPath): ZIO[PassCtx, PgpError, Password] =
    for
      passphrase       <- passwordReader.readPassphrase()
      encryptedDataOpt <- ZIO.attempt(getPubKeyEncryptedData(passPath)).mapError(e => PgpError(s"Unable to get encrypted data", e))
      encryptedData    <- ZIO.fromOption(encryptedDataOpt).orElseFail(PgpError(s"Unable to find encrypted object"))
      privateKey       <- buildPrivateKeyOrFail(passphrase, encryptedData.getKeyID)
      passwordOpt      <- ZIO.attempt(decrypt(privateKey, encryptedData)).mapError(e => PgpError(s"Unable to decrypt password", e))
      password         <- ZIO.fromOption(passwordOpt).orElseFail(PgpError(s"Unable to find literal data"))
    yield password

}

object BouncyCastleRSA:
  Security.addProvider(new BouncyCastleProvider())
