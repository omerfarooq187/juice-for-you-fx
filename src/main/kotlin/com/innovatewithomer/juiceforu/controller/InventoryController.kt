package com.innovatewithomer.juiceforu.controller

import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.*
import com.innovatewithomer.juiceforu.models.InventoryItem
import com.innovatewithomer.juiceforu.repo.InventoryRepository
import com.innovatewithomer.juiceforu.utils.DisposableController
import com.innovatewithomer.juiceforu.utils.Logger
import javafx.application.Platform
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class InventoryController : DisposableController {

    @FXML private lateinit var inventoryTable: TableView<InventoryItem>
    @FXML private lateinit var colItemId: TableColumn<InventoryItem, Int>
    @FXML private lateinit var colItemName: TableColumn<InventoryItem, String>
    @FXML private lateinit var colQuantity: TableColumn<InventoryItem, Double>
    @FXML private lateinit var colUnit: TableColumn<InventoryItem, String>

    @FXML private lateinit var itemNameField: TextField
    @FXML private lateinit var quantityField: TextField
    @FXML private lateinit var unitField: TextField

    private val repo = InventoryRepository()
    private val inventoryData = FXCollections.observableArrayList<InventoryItem>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadVersion = 0
    private var saving = false

    @FXML
    fun initialize() {
        // Setup table columns
        colItemId.setCellValueFactory { SimpleIntegerProperty(it.value.id).asObject() }
        colItemName.setCellValueFactory { SimpleStringProperty(it.value.name) }
        colQuantity.setCellValueFactory { SimpleDoubleProperty(it.value.quantity).asObject() }
        colUnit.setCellValueFactory { SimpleStringProperty(it.value.unit) }

        inventoryTable.items = inventoryData
        refreshTable()

        // ✅ Add selection listener
        inventoryTable.selectionModel.selectedItemProperty().addListener { _, _, selectedItem ->
            if (selectedItem != null) {
                itemNameField.text = selectedItem.name
                quantityField.text = selectedItem.quantity.toString()
                unitField.text = selectedItem.unit
            }
        }
    }

    private fun refreshTable() {
        val version = ++loadVersion
        ioScope.launch {
            try {
                val items = repo.getAllItems()
                Platform.runLater { if (version == loadVersion) inventoryData.setAll(items) }
            } catch (e: Exception) {
                Logger.logError(e, "Could not load inventory")
                Platform.runLater { showAlert(Alert.AlertType.ERROR, "Inventory unavailable", "Could not load inventory: ${e.message}") }
            }
        }
    }

    override fun dispose() = ioScope.cancel()

    @FXML
    fun handleAdd() {
        val name = itemNameField.text.trim()
        val qty = quantityField.text.toDoubleOrNull()
        val unit = unitField.text.trim()

        if (name.isBlank() || unit.isBlank() || qty == null || qty < 0) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Enter a name, unit, and a non-negative quantity.")
            return
        }

        // ✅ Check for duplicate by name (case-insensitive)
        val exists = inventoryData.any { it.name.equals(name, ignoreCase = true) }
        if (exists) {
            showAlert(Alert.AlertType.WARNING, "Duplicate Item", "Item '$name' already exists in inventory.")
            return
        }

        val newItem = InventoryItem(
            id = 0, // DB will auto-generate ID
            name = name,
            unit = unit,
            quantity = qty,
            reorderLevel = 5.0 // default
        )

        saveInventoryChange(onSuccess = { clearFields() }) { repo.addItem(newItem) }
    }

    @FXML
    fun handleUpdate() {
        val selected = inventoryTable.selectionModel.selectedItem ?: run {
            showAlert(Alert.AlertType.WARNING, "No item selected", "Select an inventory row to update.")
            return
        }
        val id = selected.id

        val name = itemNameField.text.trim()
        val unit = unitField.text.trim()
        val quantity = quantityField.text.toDoubleOrNull()
        if (name.isBlank() || unit.isBlank() || quantity == null || quantity < 0) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Enter a name, unit, and a non-negative quantity.")
            return
        }
        if (inventoryData.any { it.id != id && it.name.equals(name, ignoreCase = true) }) {
            showAlert(Alert.AlertType.WARNING, "Duplicate Item", "Item '$name' already exists in inventory.")
            return
        }

        val updated = selected.copy(name = name, quantity = quantity, unit = unit)
        saveInventoryChange(onSuccess = {
            inventoryData.find { it.id == id }?.let { inventoryTable.selectionModel.select(it) }
            clearFields()
        }) { repo.updateItem(updated) }
    }


    @FXML
    fun handleDelete() {
        val selected = inventoryTable.selectionModel.selectedItem ?: run {
            showAlert(Alert.AlertType.WARNING, "No item selected", "Select an inventory row to delete.")
            return
        }

        // ✅ Confirmation dialog
        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Delete Confirmation"
        alert.headerText = null
        alert.contentText = "Are you sure you want to delete '${selected.name}'?"

        val result = alert.showAndWait()
        if (result.isPresent && result.get() == ButtonType.OK) {
            saveInventoryChange("Item is in use", { clearFields() }) { repo.deleteItem(selected.id) }
        }
    }

    private fun saveInventoryChange(errorTitle: String = "Inventory change failed", onSuccess: () -> Unit = {}, change: () -> Unit) {
        if (saving) return
        saving = true
        inventoryTable.isDisable = true
        ++loadVersion
        ioScope.launch {
            try {
                change()
                val items = repo.getAllItems()
                Platform.runLater {
                    inventoryData.setAll(items)
                    onSuccess()
                }
            } catch (e: Exception) {
                Logger.logError(e, "Could not save inventory change")
                Platform.runLater {
                    val message = if (errorTitle == "Item is in use")
                        "This ingredient belongs to a recipe and cannot be deleted yet."
                    else e.message ?: "The inventory change could not be saved."
                    showAlert(Alert.AlertType.ERROR, errorTitle, message)
                }
            } finally {
                Platform.runLater {
                    saving = false
                    inventoryTable.isDisable = false
                }
            }
        }
    }

    private fun clearFields() {
        itemNameField.clear()
        quantityField.clear()
        unitField.clear()
    }

    private fun showAlert(type: Alert.AlertType, title: String, message: String) {
        val alert = Alert(type)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    // Called by OrderController
    fun deductInventoryById(ingredientId: Int, quantity: Double) {
        ioScope.launch {
            try {
                repo.adjustStock(ingredientId, -quantity)
                Platform.runLater { refreshTable() }
            } catch (e: Exception) {
                Logger.logError(e, "Could not deduct inventory")
                Platform.runLater { showAlert(Alert.AlertType.WARNING, "Stock Error", e.message ?: "Not enough stock.") }
            }
        }
    }
}
