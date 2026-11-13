package com.s3.providerandriod6

import android.content.Context
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
class LogWriter(private val context: Context) {
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logFormatter by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    private val logLock = Mutex()
    private val logChannel = Channel<String>(Channel.UNLIMITED)

    init {
        startLogConsumer()
    }

    fun writeToLog(msg: String) {
        logChannel.trySend(msg).getOrNull() ?: run {
            logScope.launch {
                logChannel.send(msg)
            }
        }
    }

    private fun startLogConsumer() {
        logScope.launch {
            for (message in logChannel) {
                processLogEntry(message)
            }
        }
    }

    private suspend fun processLogEntry(message: String) {
        logLock.withLock {
            try {
                val logDir = ensureLogDirectory() ?: return
                val logFile = getCurrentLogFile(logDir)

                val timestamp = logFormatter.format(System.currentTimeMillis())
                logFile.appendText("$timestamp: $message\n")

                // Проверка размера и ротация при необходимости
                if (logFile.length() > MAX_LOG_SIZE_BYTES) {
                    rotateLogs(logDir)
                }
            } catch (e: Exception) {
                showError("Log error: ${e.message}")
            }
        }
    }

    private fun ensureLogDirectory(): File? {
        return context.getExternalFilesDir("logs")?.also { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }

    private fun getCurrentLogFile(logDir: File): File {
        return File(logDir, "app.log").also {
            if (!it.exists()) it.createNewFile()
        }
    }

    private fun rotateLogs(logDir: File) {
        val logFile = File(logDir, "app.log")
        if (logFile.length() > MAX_LOG_SIZE_BYTES) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val rotatedFile = File(logDir, "app_$timestamp.log")
            logFile.renameTo(rotatedFile)

            // Удаляем старые логи (оставляем последние 5 файлов)
            logDir.listFiles()
                ?.filter { it.name.startsWith("app_") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_LOG_FILES)
                ?.forEach { it.delete() }
        }
    }

    suspend fun flushAndClose() {
        logChannel.close() // Останавливаем прием новых сообщений
        logScope.coroutineContext.job.children.forEach { it.join() } // Ждем завершения
        logScope.cancel()
    }

    private suspend fun showError(error: String) {

        withContext(Dispatchers.Main) {
            writeToLog(error)
        }
    }




    companion object {
        private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB
        private const val MAX_LOG_FILES = 5
    }
}
