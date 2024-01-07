package com.pragmaxim.pass

import zio.{IO, Task, ULayer, ZIO, ZLayer}

import scala.sys.process.*

trait Clipboard {
  def copy(str: String): Task[Unit]
}

object Clipboard:
  def live: ULayer[Clipboard] =
    ZLayer.succeed(
      new Clipboard {
        def copy(str: String): Task[Unit] =
          ZIO
            .attempt((s"echo -n $str" #| "xsel --clipboard").!!)
      }
    )
