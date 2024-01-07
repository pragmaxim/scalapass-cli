package com.pragmaxim.pass

sealed trait PassError extends Throwable
case class UserError(msg: String, cause: Throwable = null) extends Exception(msg, cause) with PassError
case class GitError(msg: String, cause: Throwable = null) extends Exception(msg, cause) with PassError
case class PgpError(msg: String, cause: Throwable = null) extends Exception(msg, cause) with PassError
case class SystemError(msg: String, cause: Throwable = null) extends Exception(msg, cause) with PassError
