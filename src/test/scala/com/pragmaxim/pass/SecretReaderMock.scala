package com.pragmaxim.pass

import zio.internal.stacktracer.Tracer
import zio.mock.*
import zio.{mock, Task, UIO, URLayer, ZIO, ZLayer}

object SecretReaderMock extends Mock[SecretReader]:
  object ReadPassword extends Effect[Unit, Nothing, String]
  object ReadPassPhrase extends Effect[Unit, Nothing, String]

  override val compose: URLayer[Proxy, SecretReader] =
    ZLayer.fromZIO {
      ZIO.serviceWith[Proxy] { proxy =>
        new SecretReader {
          override def readPassword(): UIO[String] = proxy(ReadPassword)

          override def readPassphrase(): UIO[String] = proxy(ReadPassPhrase)
        }
      }
    }

object ClipboardMock extends Mock[Clipboard]:
  object Copy extends Effect[String, Throwable, Unit]

  override val compose: URLayer[Proxy, Clipboard] =
    ZLayer.fromZIO {
      ZIO.serviceWith[Proxy] { proxy =>
        new Clipboard {
          override def copy(str: String): Task[Unit] = proxy(Copy, str)
        }
      }
    }
