package com.innovatewithomer.juiceforu.utils

import com.innovatewithomer.juiceforu.AppPaths
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

object Logger {

    private val logFile: File by lazy {
        val logDir = AppPaths.dataDirectory.resolve("logs").toFile()
        if (!logDir.exists()) logDir.mkdirs()
        File(logDir, "app_log.txt")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        logFile.appendText("[$timestamp] INFO: $message\n")
    }

    fun logError(error: Throwable, context: String? = "") {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val timestamp = dateFormat.format(Date())
        logFile.appendText("[$timestamp] ERROR in $context: ${error.message}\n$sw\n")
    }
}
