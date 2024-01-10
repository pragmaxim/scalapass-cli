package com.pragmaxim.pass.git

import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.*
import org.eclipse.jgit.api.{CommitCommand, Git}
import org.eclipse.jgit.gpg.bc.internal.BouncyCastleGpgSigner
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import zio.*

import java.nio.file.Path as JPath
import scala.jdk.CollectionConverters.*

case class JGit(git: Git, keyRing: KeyRing, passwordReader: SecretReader) extends GitLike:

  override def status: ZIO[PassCtx, GitError, String] =
    ZIO
      .attempt(git.status().call())
      .map(_.getUncommittedChanges.asScala)
      .mapBoth(
        er => GitError(s"Unable to get git status", er),
        {
          case changes if changes.nonEmpty =>
            s"GIT: Uncommited changes :\n${changes.mkString("\t", "\n\t", "")}"
          case _ =>
            s"GIT: Nothing to commit, working tree clean"
        }
      )

  def close(): URIO[Any, Unit] = ZIO.attempt(git.close()).ignore

  private def sign(cmd: CommitCommand, gpgId: GpgId) =
    for
      passphrase <- passwordReader.readPassphrase()
      result <- ZIO.attempt(
                  cmd
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gpgId, passphrase))
                    .setGpgSigner(new BouncyCastleGpgSigner)
                )
    yield result

  override def commitFile(message: String, signCommit: Boolean, paths: JPath*): ZIO[PassCtx, GitError, Unit] =
    (for
      _ <- ZIO.foreachDiscard(paths)(path => ZIO.attempt(git.add().addFilepattern(path.toString).call()))
      cmd = git.commit().setMessage(message)
      signedCmd <- if (signCommit) sign(cmd, keyRing.gpgId) else ZIO.succeed(cmd)
      _         <- ZIO.attempt(signedCmd.call())
    yield ()).mapError(er => GitError(s"Unable to commit and sign", er))

object JGit:
  private def initRepo(passHomeDir: PassHomeDir) =
    ZIO
      .attempt(Git.init().setDirectory(passHomeDir.toFile).call())
      .mapError(er => GitError(s"Unable to init git repo at $passHomeDir", er))

  private def openRepo(passHomeDir: PassHomeDir) =
    ZIO
      .attempt(Git.open(passHomeDir.toFile))
      .mapError(er => GitError(s"Unable to open git repo at $passHomeDir", er))

  def initGitService(): ZIO[PassCtx & SecretReader & KeyRing, GitError, JGit] =
    for
      ctx <- ZIO.service[PassCtx]
      sr  <- ZIO.service[SecretReader]
      kr  <- ZIO.service[KeyRing]
      git <- if (ctx.passDir.gitInitialized) openRepo(ctx.passDir) else initRepo(ctx.passDir)
    yield JGit(git, kr, sr)
