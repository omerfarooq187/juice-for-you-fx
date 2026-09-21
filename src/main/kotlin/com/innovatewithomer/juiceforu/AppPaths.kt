package com.innovatewithomer.juiceforu

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.sql.DriverManager

object AppPaths {
    val dataDirectory: Path by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        val userHome = Paths.get(System.getProperty("user.home"))
        val path = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                if (appData != null) Paths.get(appData, AppBrand.current.dataDirectoryName)
                else userHome.resolve("AppData").resolve("Roaming").resolve(AppBrand.current.dataDirectoryName)
            }
            os.contains("mac") -> userHome.resolve("Library").resolve("Application Support").resolve(AppBrand.current.dataDirectoryName)
            else -> {
                val xdgData = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                val directory = AppBrand.current.id
                if (xdgData != null) Paths.get(xdgData, directory)
                else userHome.resolve(".local").resolve("share").resolve(directory)
            }
        }
        path.toAbsolutePath().normalize()
    }

    val databasePath: Path get() = dataDirectory.resolve(AppBrand.current.databaseFileName)
    val settingsPath: Path get() = dataDirectory.resolve("settings.properties")
    val defaultBackupDirectory: Path get() = dataDirectory.resolve("backups")

    /**
     * Prepares the application-data directory and safely migrates the legacy
     * working-directory database once. VACUUM INTO creates a consistent copy,
     * including data that may currently be represented by SQLite's WAL.
     */
    fun prepareDatabase(): Path {
        Files.createDirectories(dataDirectory)
        val destination = databasePath
        val legacy = Paths.get(AppBrand.current.databaseFileName).toAbsolutePath().normalize()

        if (!Files.exists(destination) && Files.isRegularFile(legacy) && legacy != destination) {
            migrateDatabase(legacy, destination)
        }
        return destination
    }

    private fun migrateDatabase(source: Path, destination: Path) {
        // Stage beside the final database so a failed copy never looks like a
        // completed migration on the next startup. The legacy file is retained.
        val staging = Files.createTempFile(dataDirectory, "migration-", ".db")
        try {
            Files.delete(staging) // VACUUM INTO requires a non-existent target.
            DriverManager.getConnection("jdbc:sqlite:${source.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { it.execute("PRAGMA busy_timeout = 10000") }
                connection.prepareStatement("VACUUM INTO ?").use { statement ->
                    statement.setString(1, staging.toString())
                    statement.execute()
                }
            }
            DriverManager.getConnection("jdbc:sqlite:${staging.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA integrity_check").use { result ->
                        check(result.next() && result.getString(1) == "ok") { "Migrated database failed integrity check" }
                    }
                }
            }
            try {
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging, destination)
            }
        } finally {
            Files.deleteIfExists(staging)
        }
    }
}
