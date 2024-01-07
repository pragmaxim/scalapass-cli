package com.pragmaxim.pass.asymetric

import com.pragmaxim.pass.IOUtils
import com.pragmaxim.pass.RSA.{PassPath, PassPhrase, Password}
import org.bouncycastle.bcpg.{ArmoredOutputStream, CompressionAlgorithmTags, SymmetricKeyAlgorithmTags}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.*

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, FileOutputStream}
import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date
import scala.jdk.CollectionConverters.*

/** Highly side-effecting code that can throw exceptions for any reason, always wrap into an Effect */
trait BouncyCastleSupport {

  def buildPrivateKey(privateKey: String, passphrase: PassPhrase, keyId: Long): Option[PGPPrivateKey] =
    Option(
      new PGPSecretKeyRingCollection(
        PGPUtil.getDecoderStream(new ByteArrayInputStream(privateKey.getBytes)),
        new JcaKeyFingerprintCalculator()
      ).getSecretKey(keyId)
    )
      .flatMap { secretKey =>
        Option(secretKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(passphrase.toCharArray)))
      }

  def buildPublicKey(publicKey: String): Option[PGPPublicKey] =
    new PGPPublicKeyRingCollection(
      PGPUtil.getDecoderStream(new ByteArrayInputStream(publicKey.getBytes())),
      new JcaKeyFingerprintCalculator()
    )
      .getKeyRings()
      .asScala
      .flatMap(_.iterator().asScala.find(_.isEncryptionKey()))
      .toList
      .headOption

  def getPubKeyEncryptedData(passPath: PassPath): Option[PGPPublicKeyEncryptedData] =
    new JcaPGPObjectFactory(PGPUtil.getDecoderStream(new FileInputStream(passPath.toFile)))
      .iterator()
      .asScala
      .collectFirst { case l: PGPEncryptedDataList => l }
      .toList
      .flatMap(_.getEncryptedDataObjects.asScala)
      .collectFirst { case d: PGPPublicKeyEncryptedData => d }

  def decrypt(pgpPrivateKey: PGPPrivateKey, publicKeyEncryptedData: PGPPublicKeyEncryptedData): Option[String] = {
    val decryptorFactory      = new JcePublicKeyDataDecryptorFactoryBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(pgpPrivateKey)
    val decryptedCompressedIn = publicKeyEncryptedData.getDataStream(decryptorFactory)
    val decCompObjFac         = new JcaPGPObjectFactory(decryptedCompressedIn)
    val pgpCompressedData     = decCompObjFac.nextObject().asInstanceOf[PGPCompressedData]
    val compressedDataStream  = new BufferedInputStream(pgpCompressedData.getDataStream)
    val pgpCompObjFac         = new JcaPGPObjectFactory(compressedDataStream)

    Option(pgpCompObjFac.nextObject)
      .collect { case m: PGPLiteralData =>
        IOUtils.readStreamToString(m.getInputStream)
      }
  }

  def encrypt(publicKey: PGPPublicKey, passPath: PassPath, password: Password): Unit = {
    val encryptedGen =
      new PGPEncryptedDataGenerator(
        new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_128).setProvider(BouncyCastleProvider.PROVIDER_NAME)
      )
    val armoredOutputStream        = new ArmoredOutputStream(new FileOutputStream(passPath.toFile))
    val pgpCompressedDataGenerator = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP)
    try
      encryptedGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(publicKey))
      IOUtils.writeStringToOutput(
        password,
        new PGPLiteralDataGenerator().open(
          pgpCompressedDataGenerator
            .open(
              encryptedGen.open(
                armoredOutputStream,
                new Array[Byte](1 << 16)
              )
            ),
          PGPLiteralData.BINARY,
          PGPLiteralData.CONSOLE,
          Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)),
          new Array[Byte](1 << 16)
        )
      )
    finally {
      pgpCompressedDataGenerator.close()
      encryptedGen.close()
      armoredOutputStream.close()
    }
  }

}
