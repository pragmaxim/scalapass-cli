package com.pragmaxim.pass

import com.pragmaxim.pass.git.JGit.initGitService
import com.pragmaxim.pass.git.{SystemGit, JGit}
import zio.*

import java.nio.file.Path as JPath

trait GitLike:
  def commitFile(message: String, signCommit: Boolean, paths: JPath*): ZIO[PassCtx, GitError, Unit]
  def status: ZIO[PassCtx, GitError, String]

object GitLike:
  enum GitType(val value: String):
    case git extends GitType("git")
    case jgit extends GitType("jgit")

  def chooseGit(gitType: GitType): ZIO[KeyRing with SecretReader with PassCtx, GitError, GitLike] = gitType match
    case GitType.git  => SystemGit.initGitService()
    case GitType.jgit => ZIO.scoped(ZIO.acquireRelease(JGit.initGitService())(_.close()))

  def open: ZLayer[PassCtx & SecretReader & KeyRing, GitError, GitLike] =
    ZLayer {
      for
        ctx <- ZIO.service[PassCtx]
        gitTypePath = ctx.passDir.gitTypePath
        gitType    <- ZIO.readFile(gitTypePath).mapBoth(er => GitError(s"Password store is not initialized", er), GitType.valueOf)
        gitSession <- chooseGit(gitType)
      yield gitSession
    }

  def init(gitType: GitType): ZLayer[PassCtx & KeyRing & SecretReader, PassError, GitLike] =
    ZLayer {
      for
        ctx <- ZIO.service[PassCtx]
        gitTypePath = ctx.passDir.gitTypePath
        _          <- ZIO.attempt(ctx.passDir.toFile.mkdirs()).mapError(er => SystemError(s"Unable to create ${ctx.passDir}", er))
        gitSession <- chooseGit(gitType)
        _          <- IOUtils.writeToFile(gitTypePath, gitType.value).provideSomeLayer(ZLayer.succeed(gitSession))
      yield gitSession
    }
