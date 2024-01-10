package com.pragmaxim.pass

import com.pragmaxim.pass.GitLike.GitType
import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.PgpType
import zio.{RLayer, TaskLayer, ZLayer}

trait Layers:
  val baseLayer: TaskLayer[PassCtx & SecretReader & Clipboard] =
    PassCtx.defaultEnv >+> SecretReader.live >+> Clipboard.live

  val openLayer: RLayer[PassCtx & SecretReader & Clipboard, KeyRing & GitLike & RSA & PassService] =
    GnuPGKeyRing.open >+> GitLike.open >+> RSA.open >+> PassService.layer

  def initLayer(
    gpgId: GpgId,
    pgpType: PgpType,
    gitType: GitType
  ): ZLayer[PassCtx & SecretReader & Clipboard, PassError, KeyRing & GitLike & RSA & PassService] =
    GnuPGKeyRing.init(gpgId) >+> GitLike.init(gitType) >+> RSA.init(pgpType) >+> PassService.layer
