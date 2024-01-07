package com.pragmaxim.pass.asymetric

import com.pragmaxim.pass.RSA.{PassPhrase, Password}
import org.bouncycastle.util.io.Streams
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.HashAlgorithm
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.{EncryptionOptions, ProducerOptions}
import org.pgpainless.key.protection.CachingSecretKeyRingProtector
import org.pgpainless.policy.Policy
import org.pgpainless.util.Passphrase

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.util

/** Highly side-effecting code that can throw exceptions for any reason, always wrap into an Effect */
trait PGPainlessSupport {

  def init(): Unit =
    PGPainless.getPolicy.setSignatureHashAlgorithmPolicy(
      new Policy.HashAlgorithmPolicy(
        HashAlgorithm.SHA512,
        util.Arrays.asList(HashAlgorithm.SHA512, HashAlgorithm.SHA384, HashAlgorithm.SHA256, HashAlgorithm.SHA224, HashAlgorithm.SHA1)
      )
    )

  def encr(publicKey: String, password: Password, outputStream: OutputStream): Unit = {
    val encryptor =
      PGPainless
        .encryptAndOrSign()
        .onOutputStream(outputStream)
        .withOptions(
          ProducerOptions
            .encrypt(EncryptionOptions.encryptCommunications().addRecipient(PGPainless.readKeyRing.publicKeyRing(publicKey)))
            .setAsciiArmor(true)
        )
    try
      Streams.pipeAll(new ByteArrayInputStream(password.getBytes()), encryptor)
    finally
      encryptor.close()
  }

  def decr(privateKey: String, passphrase: PassPhrase, inputStream: InputStream): String = {
    val secretKeyRing = PGPainless.readKeyRing().secretKeyRing(privateKey)
    val ringProtector = new CachingSecretKeyRingProtector()
    ringProtector.addPassphrase(secretKeyRing, Passphrase.fromPassword(passphrase))
    val out = new ByteArrayOutputStream()
    val decryptor =
      PGPainless
        .decryptAndOrVerify()
        .onInputStream(inputStream)
        .withOptions(new ConsumerOptions().addDecryptionKey(secretKeyRing, ringProtector))
    try {
      Streams.pipeAll(decryptor, out)
      out.toString
    } finally {
      decryptor.close()
      out.close()
    }
  }

}
