package com.pragmaxim.pass

import com.pragmaxim.pass.Cli.Subcommand
import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.PgpType
import zio.*
import zio.cli.HelpDoc.Span.{spans, strong, text}
import zio.cli.{CliApp, ZIOCliDefault}

object Main extends ZIOCliDefault with Layers {

  val cliApp = CliApp.make(
    name    = "Password Store",
    version = "0.0.1",
    summary = spans(
      strong(s"Do not use with real passwords, it is just experimental!\n"),
      text(s"To change default directory : ${PassHomeDir.defaultPassDir}, set ${PassHomeDir.PASS_HOME_PATH_ENV} env variable\n"),
      text(s"Either have `gpg` executable on your PATH or set full path with ${GpgExe.PASS_GNUPG_PATH_ENV} env variable\n"),
      text(s"Either have `git` executable on your PATH or set full path with ${GitExe.PASS_GIT_PATH_ENV} env variable\n"),
      text(s"You need xsel installed to copy password to clipboard\n")
    ),
    command = Cli.pass
  ) {
    case Subcommand.Init(gpgId, pgpType, gitType, debug) =>
      PassService.printStatus
        .provide(baseLayer >+> initLayer(gpgId, pgpType, gitType))
        .debugOrFail(debug)
    case Subcommand.Insert(forced, passName, debug) =>
      PassService
        .insert(forced, passName)
        .provide(baseLayer >+> openLayer)
        .debugOrFail(debug)
    case Subcommand.Show(passName, debug) =>
      PassService
        .show(passName)
        .provide(baseLayer >+> openLayer)
        .debugOrFail(debug)
    case Subcommand.Copy(passName, debug) =>
      PassService
        .copy(passName)
        .provide(baseLayer >+> openLayer)
        .debugOrFail(debug)
    case Subcommand.List(folder, debug) =>
      PassService
        .list(folder)
        .provide(baseLayer >+> openLayer)
        .debugOrFail(debug)
  }

  implicit class ZIOOps[R, E <: Throwable, A](private val zio: ZIO[R, E, A]) extends AnyVal {

    /** Friendly and readable output for a CLI app */
    def debugOrFail(debugEnabled: Boolean) =
      zio.catchNonFatalOrDie {
        case e if debugEnabled =>
          ZIO.fail(e)
        case e =>
          Console.printLine(s"${e.getMessage}\n${Cli.pass.helpDoc.toPlaintext()}").as(ExitCode.failure)
      }

  }
}
