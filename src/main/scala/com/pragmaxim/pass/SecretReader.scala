package com.pragmaxim.pass

import zio.{UIO, ULayer, ZIO, ZLayer}

trait SecretReader {
  def readPassword(): UIO[String]
  def readPassphrase(): UIO[String]
}

object SecretReader {
  def live: ULayer[SecretReader] =
    ZLayer.succeed(
      new SecretReader {
        override def readPassword(): UIO[String] =
          for
            _          <- zio.Console.print(s"Enter password : ").orDie
            passphrase <- ZIO.attemptBlocking(System.console().readPassword()).map(new String(_)).orDie
          yield passphrase

        override def readPassphrase(): UIO[String] =
          for
            _          <- zio.Console.print(s"Enter passphrase : ").orDie
            passphrase <- ZIO.attemptBlocking(System.console().readPassword()).map(new String(_)).orDie
          yield passphrase
      }
    )
}
