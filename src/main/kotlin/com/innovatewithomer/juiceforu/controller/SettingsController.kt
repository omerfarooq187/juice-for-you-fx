package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.AppPaths
import com.innovatewithomer.juiceforu.AppSettings
import com.innovatewithomer.juiceforu.BackupService
import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.PrinterService
import com.innovatewithomer.juiceforu.utils.DisposableController
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.stage.DirectoryChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SettingsController : DisposableController {
    @FXML private lateinit var printerCombo: ComboBox<String>
    @FXML private lateinit var printerStatusLabel: Label
    @FXML private lateinit var backupDirectoryField: TextField
    @FXML private lateinit var automaticBackupCheck: CheckBox
    @FXML private lateinit var lastBackupLabel: Label
    @FXML private lateinit var databasePathLabel: Label
    @FXML private lateinit var databaseStatusLabel: Label
    @FXML private lateinit var backupNowButton: Button

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
    private var printerLookupVersion = 0

    @FXML
    fun initialize() {
        backupDirectoryField.text = AppSettings.backupDirectory.toString()
        automaticBackupCheck.isSelected = AppSettings.automaticBackupEnabled
        databasePathLabel.text = Database.currentPath()?.toString() ?: AppPaths.databasePath.toString()
        refreshLastBackupLabel()
        refreshPrinters()
        checkDatabase()
    }

    @FXML
    private fun onRefreshPrintersClick() = refreshPrinters()

    private fun refreshPrinters() {
        val version = ++printerLookupVersion
        val saved = AppSettings.printerName
        if (printerCombo.value == null && printerCombo.editor.text.isBlank()) printerCombo.value = saved
        val existingInput = printerCombo.editor.text
        printerStatusLabel.text = "Looking for printers…"
        ioScope.launch {
            try {
                val detected = PrinterService.availablePrinterNames()
                val default = if (saved.isNullOrBlank()) PrinterService.defaultPrinterName() else null
                Platform.runLater {
                    if (version != printerLookupVersion) return@runLater
                    val currentInput = printerCombo.editor.text
                    val printers = detected.toMutableList()
                    if (!saved.isNullOrBlank() && saved !in printers) printers.add(0, saved)
                    printerCombo.items = FXCollections.observableArrayList(printers)
                    if (currentInput == existingInput) printerCombo.value = saved ?: default
                    else printerCombo.editor.text = currentInput
                    printerStatusLabel.text = when {
                        detected.isEmpty() -> "No printers detected. You can type an exact system printer name."
                        saved != null && saved !in detected -> "Saved printer is currently unavailable."
                        else -> "${detected.size} printer${if (detected.size == 1) "" else "s"} available."
                    }
                }
            } catch (e: Exception) {
                Platform.runLater { printerStatusLabel.text = "Printer lookup failed: ${e.message}" }
            }
        }
    }

    @FXML
    private fun onChooseBackupDirectoryClick() {
        val chooser = DirectoryChooser().apply {
            title = "Choose backup folder"
            val current = File(backupDirectoryField.text)
            if (current.isDirectory) initialDirectory = current
        }
        chooser.showDialog(backupDirectoryField.scene.window)?.let {
            backupDirectoryField.text = it.toPath().toAbsolutePath().normalize().toString()
        }
    }

    @FXML
    private fun onSaveSettingsClick() {
        val backupPath = runCatching { java.nio.file.Path.of(backupDirectoryField.text.trim()) }.getOrNull()
        if (backupPath == null || backupDirectoryField.text.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Invalid backup folder", "Choose a valid folder for database backups.")
            return
        }

        AppSettings.printerName = printerCombo.editor.text.trim().ifBlank { printerCombo.value.orEmpty() }
        AppSettings.backupDirectory = backupPath
        AppSettings.automaticBackupEnabled = automaticBackupCheck.isSelected
        AppSettings.save()
        showAlert(Alert.AlertType.INFORMATION, "Settings saved", "Printer and backup settings were saved for this user.")
    }

    @FXML
    private fun onBackupNowClick() {
        val backupPath = runCatching { java.nio.file.Path.of(backupDirectoryField.text.trim()) }.getOrNull()
        if (backupPath == null) {
            showAlert(Alert.AlertType.WARNING, "Invalid backup folder", "Choose a valid backup folder first.")
            return
        }

        AppSettings.backupDirectory = backupPath
        AppSettings.automaticBackupEnabled = automaticBackupCheck.isSelected
        AppSettings.printerName = printerCombo.editor.text.trim().ifBlank { printerCombo.value.orEmpty() }
        AppSettings.save()
        backupNowButton.isDisable = true
        backupNowButton.text = "Backing up…"
        ioScope.launch {
            try {
                val backup = BackupService.createBackup(backupPath)
                Platform.runLater {
                    refreshLastBackupLabel()
                    showAlert(Alert.AlertType.INFORMATION, "Backup complete", "Database backup created at:\n$backup")
                }
            } catch (e: Exception) {
                Platform.runLater {
                    showAlert(Alert.AlertType.ERROR, "Backup failed", e.message ?: "The database could not be backed up.")
                }
            } finally {
                Platform.runLater {
                    backupNowButton.isDisable = false
                    backupNowButton.text = "Back up now"
                }
            }
        }
    }

    @FXML
    private fun onCheckDatabaseClick() = checkDatabase(showSuccess = true)

    private fun checkDatabase(showSuccess: Boolean = false) {
        databaseStatusLabel.text = "Checking database…"
        ioScope.launch {
            try {
                val result = Database.integrityCheck()
                Platform.runLater {
                    val healthy = result.equals("ok", ignoreCase = true)
                    databaseStatusLabel.text = if (healthy) "Healthy · SQLite integrity check passed" else "Attention required · $result"
                    if (showSuccess) showAlert(
                        if (healthy) Alert.AlertType.INFORMATION else Alert.AlertType.WARNING,
                        "Database check",
                        databaseStatusLabel.text
                    )
                }
            } catch (e: Exception) {
                Platform.runLater { databaseStatusLabel.text = "Check failed · ${e.message}" }
            }
        }
    }

    private fun refreshLastBackupLabel() {
        val timestamp = AppSettings.lastSuccessfulBackupAt
        lastBackupLabel.text = if (timestamp <= 0) "No successful backup recorded yet"
        else "Last successful backup: ${dateTimeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))}"
    }

    private fun showAlert(type: Alert.AlertType, titleText: String, message: String) {
        Alert(type).apply {
            title = titleText
            headerText = null
            contentText = message
        }.showAndWait()
    }

    override fun dispose() {
        ioScope.cancel()
    }
}
