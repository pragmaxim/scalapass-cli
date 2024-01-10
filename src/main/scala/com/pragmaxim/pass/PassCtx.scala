package com.pragmaxim.pass

import com.pragmaxim.pass.PassCtx.*
import com.pragmaxim.pass.PassService.PassName
import com.pragmaxim.pass.RSA.PassPath
import zio.{ULayer, ZLayer}

import java.io.File
import java.nio.file.Path as JPath

opaque type PassHomeDir = JPath
object PassHomeDir:
  val PASS_HOME_PATH_ENV                     = "PASS_HOME_PATH"
  val defaultPassDir: PassHomeDir            = JPath.of(System.getProperty("user.home"), ".scalapass")
  def apply(): PassHomeDir                   = sys.env.get(PASS_HOME_PATH_ENV).map(JPath.of(_)).getOrElse(defaultPassDir)
  def overriden(passDir: JPath): PassHomeDir = passDir

  extension (d: PassHomeDir)
    def toFile: File                   = d.toFile
    def gpgIdPath: GpgIdPath           = d.resolve(".gpg-id")
    def pgpTypePath: GpgIdPath         = d.resolve(".pgp-type")
    def gitTypePath: GpgIdPath         = d.resolve(".git-type")
    def relativize(p: JPath)           = d.relativize(p)
    def gitInitialized: Boolean        = d.resolve(".git").toFile.exists()
    def resolveDir(path: JPath): JPath = d.resolve(path)
    def fullPasswordPath(passwordPath: PassName): PassPath =
      d.resolve(passwordPath).resolveSibling(passwordPath.toFile.getName + ".gpg")
    def relativePasswordPath(passwordPath: PassName): PassPath =
      passwordPath.resolveSibling(passwordPath.toFile.getName + ".gpg")

opaque type GpgExe = String
object GpgExe:
  val PASS_GNUPG_PATH_ENV = "PASS_GNUPG_PATH"
  def apply(): GpgExe     = sys.env.getOrElse(PASS_GNUPG_PATH_ENV, "gpg").trim

  extension (ge: GpgExe) def trim: String = ge.trim

opaque type GitExe = String
object GitExe:
  val PASS_GIT_PATH_ENV = "PASS_GIT_PATH"

  def apply(): GitExe = sys.env.getOrElse(PASS_GIT_PATH_ENV, "git").trim

  extension (ge: GitExe) def trim: String = ge.trim

case class PassCtx(passDir: PassHomeDir, gpgExe: GpgExe, gitExe: GitExe) {
  override def toString: String = s"context($passDir, $gpgExe)"
}

object PassCtx:
  type GpgIdPath = JPath

  val defaultEnv: ULayer[PassCtx] =
    ZLayer.succeed(PassCtx(PassHomeDir(), GpgExe(), GitExe()))

  def env(passDir: PassHomeDir, gpgExe: GpgExe, gitExe: GitExe): ULayer[PassCtx] =
    ZLayer.succeed(PassCtx(passDir, gpgExe, gitExe))
