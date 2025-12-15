package com.innovatewithomer.juiceforu.controller

import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.*
import com.innovatewithomer.juiceforu.models.InventoryItem
import com.innovatewithomer.juiceforu.repo.InventoryRepository
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty

class InventoryController {

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

    @FXML
    fun initialize() {
        // Setup table columns
        colItemId.setCellValueFactory { SimpleIntegerProperty(it.value.id).asObject() }
        colItemName.setCellValueFactory { SimpleStringProperty(it.value.name) }
        colQuantity.setCellValueFactory { SimpleDoubleProperty(it.value.quantity).asObject() }
        colUnit.setCellValueFactory { SimpleStringProperty(it.value.unit) }

        // Load data from DB
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
        inventoryData.setAll(repo.getAllItems())
        inventoryTable.items = inventoryData
    }

    @FXML
    fun handleAdd() {
        val name = itemNameField.text.trim()
        val qty = quantityField.text.toDoubleOrNull() ?: 0.0
        val unit = unitField.text.trim()

        if (name.isBlank() || unit.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please fill all fields.")
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

        repo.addItem(newItem) // save to DB
        refreshTable()
        clearFields()
    }

    @FXML
    fun handleUpdate() {
        val selected = inventoryTable.selectionModel.selectedItem ?: return
        val id = selected.id

        selected.name = itemNameField.text.trim()
        selected.quantity = quantityField.text.toDoubleOrNull() ?: 0.0
        selected.unit = unitField.text.trim()

        repo.updateItem(selected) // update in DB
        refreshTable()

        // ✅ Reselect updated item
        val updatedItem = inventoryData.find { it.id == id }
        if (updatedItem != null) {
            inventoryTable.selectionModel.select(updatedItem)
        }

         clearFields()
    }


    @FXML
    fun handleDelete() {
        val selected = inventoryTable.selectionModel.selectedItem ?: return

        // ✅ Confirmation dialog
        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Delete Confirmation"
        alert.headerText = null
        alert.contentText = "Are you sure you want to delete '${selected.name}'?"

        val result = alert.showAndWait()
        if (result.isPresent && result.get() == ButtonType.OK) {
            repo.deleteItem(selected.id) // delete from DB
            refreshTable()
            clearFields()
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
        val item = repo.getItemById(ingredientId)
        if (item != null && item.quantity >= quantity) {
            repo.adjustStock(ingredientId, -quantity)
            refreshTable()
        } else {
            showAlert(Alert.AlertType.WARNING, "Stock Error", "⚠️ Not enough stock for ${item?.name ?: "Unknown ingredient"}")
        }
    }
}
