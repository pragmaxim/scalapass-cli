package com.pragmaxim.pass

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}


object IOUtils {
  def writeStringToOutput(input: String, out: OutputStream): Unit = {
    val length = input.length()
    val in     = new ByteArrayInputStream(input.getBytes())
    val buff   = new Array[Byte](1 << 16)
    try {
      var len: Int                = 0
      var totalBytesWritten: Long = 0L
      while ({
        len = in.read(buff); totalBytesWritten <= length && len > 0
      }) {
        out.write(buff, 0, len)
        totalBytesWritten += len
      }
    } finally {
      out.close()
      in.close()
    }
  }

  def readStreamToString(inputStream: InputStream): String = {
    val outputStream = new ByteArrayOutputStream()
    try {
      val buff      = new Array[Byte](1 << 16)
      var bytesRead = 0
      while ({ bytesRead = inputStream.read(buff); bytesRead != -1 })
        outputStream.write(buff, 0, bytesRead)
      outputStream.toString
    } finally {
      outputStream.close()
      inputStream.close()
    }
  }
}
