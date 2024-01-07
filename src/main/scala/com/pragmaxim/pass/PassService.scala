package com.pragmaxim.pass

import com.pragmaxim.pass.PassService.{PassFolder, PassName}
import com.pragmaxim.pass.RSA.{PassPath, Password}
import zio.{Chunk, IO, Task, UIO, URIO, ZIO, ZLayer}

import java.nio.file.Path as JPath
import zio.Console.printLine

case class PassService(gitService: GitSession, rsa: RSA, passwordReader: SecretReader, clipboard: Clipboard):

  def printStatus: URIO[PassCtx & GitSession & RSA, Unit] =
    for
      ctx    <- ZIO.service[PassCtx]
      git    <- ZIO.service[GitSession]
      rsa    <- ZIO.service[RSA]
      status <- ZIO.collectAll(List(git.status, rsa.status, ZIO.succeed(ctx.toString)))
      _      <- printLine(status.mkString("\n")).ignore
    yield ()

  def insert(forced: Boolean, passName: PassName): ZIO[PassCtx, PassError, Unit] = {
    def createFile(passPath: PassPath): Task[PassPath] =
      if (forced && passPath.toFile.exists())
        printLine(s"Replacing password $passPath") *> ZIO
          .attempt(passPath.toFile.delete() && passPath.toFile.createNewFile())
          .as(passPath)
      else
        printLine(s"Creating new password $passPath") *> ZIO
          .attempt(passPath.getParent.toFile.mkdirs())
          .as(passPath)

    for
      ctx          <- ZIO.service[PassCtx]
      password     <- passwordReader.readPassword()
      fullPassPath <- createFile(ctx.passDir.fullPasswordPath(passName)).mapError(er => UserError(s"Unable to insert password under $passName", er))
      _            <- rsa.encrypt(fullPassPath, password)
      _            <- gitService.commitFileAndSign(s"Inserting password $passName", passName)(rsa.sign)
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

  def printStatus: URIO[PassService & PassCtx & GitSession & RSA, Unit] =
    ZIO.serviceWithZIO[PassService](_.printStatus)

  def insert(forced: Boolean, passName: PassName): ZIO[PassService & PassCtx, PassError, Unit] =
    ZIO.serviceWithZIO[PassService](_.insert(forced, passName))

  def show(passName: PassName): ZIO[PassService & PassCtx, PassError, Password] =
    ZIO.serviceWithZIO[PassService](_.show(passName))

  def copy(passName: PassName): ZIO[PassService & PassCtx, PassError, Password] =
    ZIO.serviceWithZIO[PassService](_.copy(passName))

  def list(subFolder: PassFolder): ZIO[PassService & PassCtx, PassError, Chunk[String]] =
    ZIO.serviceWithZIO[PassService](_.list(subFolder))

  def layer: ZLayer[GitSession & RSA & SecretReader & Clipboard, GitError, PassService] = ZLayer.derive[PassService]
