package com.pragmaxim.pass

import com.pragmaxim.pass.KeyRing.GpgId
import com.pragmaxim.pass.RSA.PgpType
import zio.{RLayer, TaskLayer}

trait Layers:
  val baseLayer: TaskLayer[PassCtx & GitSession & SecretReader & Clipboard] =
    PassCtx.defaultEnv >+> JGitSession.live >+> SecretReader.live >+> Clipboard.live

  val openLayer: RLayer[PassCtx & GitSession & SecretReader & Clipboard, KeyRing & RSA & PassService] =
    GnuPGKeyRing.open >+> RSA.open >+> PassService.layer

  def initLayer(gpgId: GpgId, pgpType: PgpType): RLayer[PassCtx & GitSession & SecretReader & Clipboard, KeyRing & RSA & PassService] =
    GnuPGKeyRing.init(gpgId) >+> RSA.init(pgpType) >+> PassService.layer
