package com.gameton.app.logging

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class JournalSeverity {
    Info,
    Warning,
    Error,
    Critical
}

enum class JournalSource {
    Local,
    Server,
    Analysis
}

data class JournalRecord(
    val timestamp: LocalDateTime,
    val source: JournalSource,
    val severity: JournalSeverity,
    val title: String,
    val message: String
)

class PlantationActionLogger(
    private val logFile: Path = defaultLogFile()
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    init {
        Files.createDirectories(logFile.parent)
    }

    fun filePath(): String = logFile.toAbsolutePath().toString()

    @Synchronized
    fun append(record: JournalRecord) {
        val line = buildString {
            append(record.timestamp.format(formatter))
            append(" [")
            append(record.source.name.uppercase())
            append("] [")
            append(record.severity.name.uppercase())
            append("] ")
            append(record.title)
            append(" :: ")
            append(record.message)
            append('\n')
        }
        Files.writeString(
            logFile,
            line,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    companion object {
        private fun defaultLogFile(): Path {
            val home = System.getProperty("user.home")
            return Paths.get(home, ".gameton_test", "logs", "plantation-actions.log")
        }
    }
}
