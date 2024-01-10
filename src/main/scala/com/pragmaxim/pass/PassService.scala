package com.pragmaxim.pass

import com.pragmaxim.pass.PassService.{PassFolder, PassName}
import com.pragmaxim.pass.RSA.{PassPath, Password}
import zio.Console.printLine
import zio.{Chunk, ZIO, ZLayer}

import java.nio.file.Path as JPath

case class PassService(gitService: GitLike, rsa: RSA, passwordReader: SecretReader, clipboard: Clipboard):

  def printStatus: ZIO[PassCtx & RSA & GitLike, GitError, Unit] =
    for
      ctx    <- ZIO.service[PassCtx]
      git    <- ZIO.service[GitLike]
      rsa    <- ZIO.service[RSA]
      status <- ZIO.collectAll(List(rsa.status, ZIO.succeed(ctx.toString), git.status))
      _      <- printLine(s"Initialized with ${status.mkString("\n")}").ignore
    yield ()

  def insert(forced: Boolean, passName: PassName): ZIO[PassCtx, PassError, Unit] = {
    def createFile(passPath: PassPath): ZIO[Any, UserError, PassPath] =
      if (forced && passPath.toFile.exists())
        ZIO
          .attempt(passPath.toFile.delete() && passPath.toFile.createNewFile())
          .mapBoth(er => UserError(s"Unable to insert password under $passName", er), _ => passPath)
      else if (passPath.toFile.exists())
        ZIO.fail(UserError(s"Password $passName already exists"))
      else
        ZIO
          .attempt(passPath.getParent.toFile.mkdirs())
          .as(passPath)
          .orDie

    for
      ctx          <- ZIO.service[PassCtx]
      password     <- passwordReader.readPassword()
      fullPassPath <- createFile(ctx.passDir.fullPasswordPath(passName))
      _            <- rsa.encrypt(fullPassPath, password)
      _            <- gitService.commitFile(s"Inserting password $passName", signCommit = true, ctx.passDir.relativePasswordPath(passName))
      _            <- printLine(s"Password $passName inserted").ignore
    yield ()
  }

  def show(passName: PassName): ZIO[PassCtx, PassError, Password] =
    for
      ctx <- ZIO.service[PassCtx]
      fullPassPath = ctx.passDir.fullPasswordPath(passName)
      password <- rsa.decrypt(fullPassPath)
      _        <- printLine(password).orDie
    yield password

  def copy(passName: PassName): ZIO[PassCtx, PassError, Password] =
    for
      ctx <- ZIO.service[PassCtx]
      fullPassPath = ctx.passDir.fullPasswordPath(passName)
      password <- rsa.decrypt(fullPassPath)
      _        <- clipboard.copy(password).mapError(er => UserError(s"Unable to copy password $passName", er))
    yield password

  def list(folder: PassFolder): ZIO[PassCtx, PassError, Chunk[String]] =
    for
      ctx <- ZIO.service[PassCtx]
      treeLines <- ZIO
                     .attempt(PassService.collectDirTree(ctx.passDir.resolveDir(folder).toFile))
                     .mapError(er => UserError(s"Unable to list passwords under $folder", er))
      _ <- printLine(treeLines.mkString("\n")).orDie
    yield treeLines

object PassService:
  import zio.Chunk

  import java.io.File

  type PassName   = JPath
  type PassFolder = JPath

  /** shell tree command output line by line with indentation with file name post-processing */
  private def collectDirTree(file: File, lines: Chunk[String] = Chunk.empty, indent: String = ""): Chunk[String] =
    if (file.isDirectory && !file.getName.startsWith(".git"))
      val (dir, nextIndent) =
        if (file.getName == ".")
          lines -> ""
        else
          lines.appended(s"$indent${file.getName}/") -> s"$indent  "

      Option(file.listFiles)
        .map(Chunk.fromArray)
        .getOrElse(Chunk.empty[File])
        .sortBy(_.getName)
        .foldLeft(dir) { case (acc, f) =>
          collectDirTree(f, acc, nextIndent)
        }
    else if (!file.getName.startsWith("."))
      lines.appended(s"$indent${file.getName.stripSuffix(".gpg")}")
    else
      lines

  def printStatus: ZIO[PassService & PassCtx & RSA & GitLike, GitError, Unit] =
    ZIO.serviceWithZIO[PassService](_.printStatus)

  def insert(forced: Boolean, passName: PassName): ZIO[PassService & PassCtx, PassError, Unit] =
    ZIO.serviceWithZIO[PassService](_.insert(forced, passName))

  def show(passName: PassName): ZIO[PassService & PassCtx, PassError, Password] =
    ZIO.serviceWithZIO[PassService](_.show(passName))

  def copy(passName: PassName): ZIO[PassService & PassCtx, PassError, Password] =
    ZIO.serviceWithZIO[PassService](_.copy(passName))

  def list(subFolder: PassFolder): ZIO[PassService & PassCtx, PassError, Chunk[String]] =
    ZIO.serviceWithZIO[PassService](_.list(subFolder))

  def layer: ZLayer[GitLike & RSA & SecretReader & Clipboard, GitError, PassService] = ZLayer.derive[PassService]
