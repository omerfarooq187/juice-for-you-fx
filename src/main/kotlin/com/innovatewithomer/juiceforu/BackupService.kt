package com.innovatewithomer.juiceforu

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BackupService {
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun createBackup(directory: Path = AppSettings.backupDirectory): Path {
        Files.createDirectories(directory)
        val timestamp = fileTimestamp.format(java.time.LocalDateTime.now())
        val prefix = AppBrand.current.backupFilePrefix
        var destination = directory.resolve("$prefix-$timestamp.db")
        var suffix = 1
        while (Files.exists(destination)) {
            destination = directory.resolve("$prefix-$timestamp-$suffix.db")
            suffix++
        }

        Database.backupTo(destination)
        AppSettings.lastSuccessfulBackupAt = System.currentTimeMillis()
        AppSettings.save()
        return destination
    }

    fun createAutomaticBackupIfDue(): Path? {
        if (!AppSettings.automaticBackupEnabled) return null
        val lastBackupDate = AppSettings.lastSuccessfulBackupAt
            .takeIf { it > 0 }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        return if (lastBackupDate == LocalDate.now()) null else createBackup()
    }
}
