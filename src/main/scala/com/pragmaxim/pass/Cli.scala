package com.pragmaxim.pass

import com.pragmaxim.pass.GitLike.GitType
import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.PassService.{PassFolder, PassName}
import com.pragmaxim.pass.RSA.PgpType
import zio.cli.*

import java.nio.file.Path as JPath

object Cli {

  sealed trait Subcommand extends Product with Serializable
  object Subcommand {
    final case class Insert(forced: Boolean, passName: PassName, debug: Boolean) extends Subcommand
    final case class Init(gpgId: GpgId, pgpType: PgpType, gitType: GitType, debug: Boolean) extends Subcommand
    final case class Show(passName: PassName, debug: Boolean) extends Subcommand
    final case class Copy(passName: PassName, debug: Boolean) extends Subcommand
    final case class List(folder: PassFolder, debug: Boolean) extends Subcommand
  }

  private val pgpTypeOptions = Options.enumeration("pgp-type")(PgpType.values.map(e => e.value -> e).toSeq: _*).withDefault(PgpType.gnupg)
  private val gitTypeOptions = Options.enumeration("git-type")(GitType.values.map(e => e.value -> e).toSeq: _*).withDefault(GitType.git)

  private val init =
    Command(
      "init",
      Options.boolean("debug") ++ pgpTypeOptions ++ gitTypeOptions,
      Args.text("gpg-id")
    ).withHelp(HelpDoc.p("Initialize store with git and pgp implementation types"))
      .map { case ((debug, pgpType, gitType), gpgId) => Subcommand.Init(gpgId, pgpType, gitType, debug) }

  private val insert =
    Command("insert", Options.boolean("debug") ++ Options.boolean("f"), Args.directory("pass-name"))
      .withHelp(HelpDoc.p("Insert password in a folder format, eg. web/google.com/foo@gmail.com"))
      .map { case ((debug, forced), passName) => Subcommand.Insert(forced, passName, debug) }

  private val show =
    Command("show", Options.boolean("debug"), Args.directory("pass-name"))
      .withHelp(HelpDoc.p("Show password"))
      .map { case (debug, passName) => Subcommand.Show(passName, debug) }

  private val copy =
    Command("cp", Options.boolean("debug"), Args.directory("pass-name"))
      .withHelp(HelpDoc.p("Copy password"))
      .map { case (debug, passName) => Subcommand.Copy(passName, debug) }

  private val ls =
    Command("ls", Options.boolean("debug"), Args.directory("folder"))
      .withHelp(HelpDoc.p("List passwords of given folder"))
      .map { case (debug, passName) => Subcommand.List(passName, debug) }

  private val tree =
    Command("tree", Options.boolean("debug"))
      .withHelp(HelpDoc.p("List passwords"))
      .map(debug => Subcommand.List(JPath.of("."), debug))

  val pass: Command[Subcommand] =
    Command("scalapass-cli", Options.none, Args.none).subcommands(init, insert, show, copy, ls, tree)

}
