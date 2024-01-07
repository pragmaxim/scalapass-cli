package com.pragmaxim.pass

import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.PassService.{PassFolder, PassName}
import com.pragmaxim.pass.RSA.PgpType
import zio.cli.*

import java.nio.file.Path as JPath

object Cli {

  sealed trait Subcommand extends Product with Serializable
  object Subcommand {
    final case class Insert(forced: Boolean, passName: PassName, debug: Boolean) extends Subcommand
    final case class Init(gpgId: GpgId, pgpType: PgpType, debug: Boolean) extends Subcommand
    final case class Show(passName: PassName, debug: Boolean) extends Subcommand
    final case class Copy(passName: PassName, debug: Boolean) extends Subcommand
    final case class List(folder: PassFolder, debug: Boolean) extends Subcommand
  }

  private val init =
    Command(
      "init",
      Options.boolean("debug") ++ Options.enumeration("pgp-type")(PgpType.values.map(e => e.value -> e).toSeq: _*).withDefault(PgpType.gnupg),
      Args.text("gpg-id")
    )
      .withHelp(HelpDoc.p("Init subcommand description"))
      .map { case ((debug, pgpType), gpgId) => Subcommand.Init(gpgId, pgpType, debug) }

  private val insert =
    Command("insert", Options.boolean("debug") ++ Options.boolean("f"), Args.directory("pass-name"))
      .withHelp(HelpDoc.p("Insert subcommand description"))
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
    Command("pass", Options.none, Args.none).subcommands(init, insert, show, copy, ls, tree)

}
