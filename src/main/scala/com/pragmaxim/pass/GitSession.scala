package com.pragmaxim.pass

import org.eclipse.jgit.api.{CommitCommand, Git}
import zio.*

import scala.jdk.CollectionConverters.*
import java.nio.file.Path as JPath

trait GitSession:
  def commitFileAndSign(message: String, paths: JPath*)(sign: CommitCommand => IO[PgpError, CommitCommand]): ZIO[PassCtx, PassError, Unit]
  def close(): IO[GitError, Unit]
  def status: UIO[String]

case class JGitSession(git: Git) extends GitSession:

  override def status: UIO[String] =
    ZIO
      .attempt(git.status().call())
      .map(_.getUncommittedChanges.asScala)
      .map {
        case changes if changes.nonEmpty =>
          s"GIT: Uncommited changes :\n${changes.mkString("\t", "\n\t", "")}"
        case _ =>
          s"GIT: Nothing to commit, working tree clean"
      }
      .orDie

  override def close(): URIO[Any, Unit] = ZIO.attempt(git.close()).ignore

  override def commitFileAndSign(message: String, paths: JPath*)(sign: CommitCommand => IO[PgpError, CommitCommand]): ZIO[PassCtx, PassError, Unit] =
    (for
      _   <- ZIO.foreachDiscard(paths)(path => ZIO.attempt(git.add().addFilepattern(path.toString).call()))
      cmd <- sign(git.commit().setMessage(message))
      _   <- ZIO.attempt(cmd.call())
    yield ()).mapError(er => GitError(s"Unable to commit", er))

object JGitSession:
  private def initRepo(passHomeDir: PassHomeDir) =
    ZIO
      .attempt(Git.init().setDirectory(passHomeDir.toFile).call())
      .mapError(er => GitError(s"Unable to init git repo at $passHomeDir", er))

  private def openRepo(passHomeDir: PassHomeDir) =
    ZIO
      .attempt(Git.open(passHomeDir.toFile))
      .mapError(er => GitError(s"Unable to open git repo at $passHomeDir", er))

  private def initGitService(): ZIO[PassCtx, GitError, JGitSession] =
    for
      ctx <- ZIO.service[PassCtx]
      git <- if (ctx.passDir.gitInitialized) openRepo(ctx.passDir) else initRepo(ctx.passDir)
    yield JGitSession(git)

  val live: ZLayer[PassCtx, GitError, JGitSession] =
    ZLayer.fromZIO(ZIO.scoped(ZIO.acquireRelease(initGitService())(_.close())))
