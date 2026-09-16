package com.innovatewithomer.juiceforu

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.util.Properties

object AppSettings {
    private const val PRINTER_NAME = "printer.name"
    private const val BACKUP_DIRECTORY = "backup.directory"
    private const val AUTOMATIC_BACKUP = "backup.automatic"
    private const val LAST_BACKUP_AT = "backup.lastSuccessfulAt"

    private val lock = Any()
    private val properties = Properties()
    private var loaded = false

    var printerName: String?
        get() = read(PRINTER_NAME)?.takeIf { it.isNotBlank() }
        set(value) = write(PRINTER_NAME, value.orEmpty())

    var backupDirectory: Path
        get() = read(BACKUP_DIRECTORY)?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: AppPaths.defaultBackupDirectory
        set(value) = write(BACKUP_DIRECTORY, value.toAbsolutePath().normalize().toString())

    var automaticBackupEnabled: Boolean
        get() = read(AUTOMATIC_BACKUP)?.toBooleanStrictOrNull() ?: true
        set(value) = write(AUTOMATIC_BACKUP, value.toString())

    var lastSuccessfulBackupAt: Long
        get() = read(LAST_BACKUP_AT)?.toLongOrNull() ?: 0L
        set(value) = write(LAST_BACKUP_AT, value.toString())

    fun save() {
        synchronized(lock) {
            ensureLoaded()
            Files.createDirectories(AppPaths.dataDirectory)
            val temporary = Files.createTempFile(AppPaths.dataDirectory, "settings-", ".tmp")
            try {
                Files.newOutputStream(temporary).use { properties.store(it, "Juice For U application settings") }
                try {
                    Files.move(temporary, AppPaths.settingsPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, AppPaths.settingsPath, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    private fun read(key: String): String? = synchronized(lock) {
        ensureLoaded()
        properties.getProperty(key)
    }

    private fun write(key: String, value: String) {
        synchronized(lock) {
            ensureLoaded()
            properties.setProperty(key, value)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        if (Files.isRegularFile(AppPaths.settingsPath)) {
            Files.newInputStream(AppPaths.settingsPath).use(properties::load)
        }
        loaded = true
    }
}
