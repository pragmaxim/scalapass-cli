package com.pragmaxim.pass.git

import com.pragmaxim.pass.*
import zio.*

import java.nio.file.Path as JPath
import scala.sys.process.*

case class SystemGit(keyRing: KeyRing) extends GitLike:
  override def status: ZIO[PassCtx, GitError, String] =
    for
      ctx <- ZIO.service[PassCtx]
      statusCommand = s"${ctx.gitExe} -C ${ctx.passDir} status"
      status <- ZIO.attempt(statusCommand.!!).mapError(er => GitError(s"Git status failed", er))
    yield status

  override def commitFile(message: String, signCommit: Boolean, paths: JPath*): ZIO[PassCtx, GitError, Unit] =
    for
      ctx <- ZIO.service[PassCtx]
      _ <- ZIO
             .foreachDiscard(paths)(path => ZIO.attempt(s"${ctx.gitExe} -C ${ctx.passDir} add $path".!!))
             .mapError(er => GitError(s"Git add failed", er))
      signed        = if signCommit then "-S" else ""
      commitCommand = s"${ctx.gitExe} -C ${ctx.passDir} commit $signed --gpg-sign=${keyRing.gpgId} -m \"$message\""
      _ <- zio.Console.printLine(commitCommand).orDie
      _ <- ZIO.attempt(commitCommand.!!).mapError(er => GitError(s"Git commit failed", er))
    yield ()

object SystemGit:
  private def initRepo(passHomeDir: PassHomeDir) =
    for
      ctx <- ZIO.service[PassCtx]
      gitCommand = s"${ctx.gitExe} -C $passHomeDir init"
      _ <- ZIO.attempt(gitCommand.!!).mapError(er => GitError(s"Git init failed", er))
    yield ()

  def initGitService(): ZIO[PassCtx & KeyRing, GitError, GitLike] =
    for
      ctx <- ZIO.service[PassCtx]
      kr  <- ZIO.service[KeyRing]
      _   <- ZIO.when(!ctx.passDir.gitInitialized)(initRepo(ctx.passDir))
    yield SystemGit(kr)
