package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.models.MenuItem
import com.innovatewithomer.juiceforu.repo.MenuItemRepository
import com.innovatewithomer.juiceforu.utils.DisposableController
import com.innovatewithomer.juiceforu.utils.Logger
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MenuController : DisposableController {

    @FXML private lateinit var menuTable: TableView<MenuItem>
    @FXML private lateinit var colId: TableColumn<MenuItem, Int>
    @FXML private lateinit var colCategory: TableColumn<MenuItem, String>
    @FXML private lateinit var colName: TableColumn<MenuItem, String>
    @FXML private lateinit var colSize: TableColumn<MenuItem, String>
    @FXML private lateinit var colPrice: TableColumn<MenuItem, Double>

    @FXML private lateinit var txtCategory: TextField
    @FXML private lateinit var txtName: TextField
    @FXML private lateinit var txtSize: TextField
    @FXML private lateinit var txtPrice: TextField

    private val menuItems = FXCollections.observableArrayList<MenuItem>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadVersion = 0
    private var saving = false

    @FXML private lateinit var colActions: TableColumn<MenuItem, Void>

    @FXML
    fun initialize() {
        // existing column setup...
        colId.setCellValueFactory { javafx.beans.property.SimpleIntegerProperty(it.value.id ?: 0).asObject() }
        colCategory.setCellValueFactory { javafx.beans.property.SimpleStringProperty(it.value.category) }
        colName.setCellValueFactory { javafx.beans.property.SimpleStringProperty(it.value.name) }
        colSize.setCellValueFactory { javafx.beans.property.SimpleStringProperty(it.value.size) }
        colPrice.setCellValueFactory { javafx.beans.property.SimpleDoubleProperty(it.value.price).asObject() }

        // ✅ add Edit/Delete buttons
        addActionButtons()

        menuTable.items = menuItems
        refreshTable()
    }

    private fun addActionButtons() {
        colActions.setCellFactory {
            object : TableCell<MenuItem, Void>() {
                private val btnEdit = Button("Edit").apply {
                    styleClass.add("button-edit")
                }
                private val btnDelete = Button("Delete").apply {
                    styleClass.add("button-delete")
                }

                private val actionBox = HBox(8.0, btnEdit, btnDelete).apply {
                    style = "-fx-alignment: CENTER_LEFT;"
                }
                init {
                    btnEdit.setOnAction {
                        val item = tableRow.item ?: return@setOnAction
                        onEditItem(item)
                    }
                    btnDelete.setOnAction {
                        val item = tableRow.item ?: return@setOnAction
                        onDeleteItem(item)
                    }
                }

                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    graphic = if (empty) null else actionBox
                }
            }
        }
    }

    private fun onEditItem(item: MenuItem) {
        val dialog = Dialog<MenuItem>()
        dialog.title = "Edit Menu Item"

        val categoryField = TextField(item.category)
        val nameField = TextField(item.name)
        val sizeField = TextField(item.size)
        val priceField = TextField(item.price.toString())

        val grid = GridPane().apply {
            hgap = 15.0
            vgap = 12.0
            padding = javafx.geometry.Insets(20.0, 20.0, 20.0, 20.0)

            add(Label("Category:"), 0, 0); add(categoryField, 1, 0)
            add(Label("Name:"), 0, 1); add(nameField, 1, 1)
            add(Label("Size:"), 0, 2); add(sizeField, 1, 2)
            add(Label("Price:"), 0, 3); add(priceField, 1, 3)
        }

        // Make fields a bit wider so they don’t look cramped
        listOf(categoryField, nameField, sizeField, priceField).forEach {
            it.prefWidth = 200.0
        }

        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.setResultConverter { button ->
            if (button == ButtonType.OK) {
                val category = categoryField.text.trim()
                val name = nameField.text.trim()
                val size = sizeField.text.trim()
                val price = priceField.text.toDoubleOrNull()
                if (category.isBlank() || name.isBlank() || size.isBlank() || price == null || price <= 0) null
                else item.copy(category = category, name = name, size = size, price = price)
            } else null
        }

        val result = dialog.showAndWait()
        if (result.isPresent) {
            saveMenuChange { MenuItemRepository.updateMenuItem(result.get()) }
        }
    }



    private fun onDeleteItem(item: MenuItem) {
        val confirm = Alert(Alert.AlertType.CONFIRMATION)
        confirm.title = "Delete Confirmation"
        confirm.headerText = null
        confirm.contentText = "Are you sure you want to delete '${item.name}'?"

        val result = confirm.showAndWait()
        if (result.isPresent && result.get() == ButtonType.OK) {
            saveMenuChange("Item is in use") { MenuItemRepository.deleteMenuItem(item.id ?: return@saveMenuChange) }
        }
    }


    @FXML
    fun onAddItemClick() {
        val category = txtCategory.text.trim()
        val name = txtName.text.trim()
        val size = txtSize.text.trim()
        val priceText = txtPrice.text.trim()

        if (category.isEmpty() || name.isEmpty() || size.isEmpty() || priceText.isEmpty()) {
            showAlert("Error", "Please fill all fields.")
            return
        }

        val price = priceText.toDoubleOrNull()
        if (price == null || price <= 0) {
            showAlert("Invalid price", "Price must be a number greater than zero.")
            return
        }

        if (menuItems.any { it.category.equals(category, true) && it.name.equals(name, true) && it.size.equals(size, true) }) {
            showAlert("Duplicate item", "That menu item and size already exist.")
            return
        }

        val newItem = MenuItem(
            id = 0, // will be auto-generated by DB
            category = category,
            name = name,
            size = size,
            price = price
        )

        saveMenuChange(onSuccess = {
            txtCategory.clear()
            txtName.clear()
            txtSize.clear()
            txtPrice.clear()
        }) { MenuItemRepository.addMenuItem(newItem) }
    }

    private fun refreshTable() {
        val version = ++loadVersion
        ioScope.launch {
            try {
                val items = MenuItemRepository.getAllMenuItems()
                Platform.runLater { if (version == loadVersion) menuItems.setAll(items) }
            } catch (e: Exception) {
                Logger.logError(e, "Could not load menu items")
                Platform.runLater { showAlert("Menu unavailable", "Could not load menu items: ${e.message}") }
            }
        }
    }

    private fun saveMenuChange(errorTitle: String = "Menu change failed", onSuccess: () -> Unit = {}, change: () -> Unit) {
        if (saving) return
        saving = true
        menuTable.isDisable = true
        ++loadVersion
        ioScope.launch {
            try {
                change()
                val items = MenuItemRepository.getAllMenuItems()
                Platform.runLater {
                    menuItems.setAll(items)
                    onSuccess()
                }
            } catch (e: Exception) {
                Logger.logError(e, "Could not save menu change")
                Platform.runLater {
                    val message = if (errorTitle == "Item is in use")
                        "This item is linked to an order or recipe and cannot be deleted yet."
                    else e.message ?: "The menu change could not be saved."
                    showAlert(errorTitle, message)
                }
            } finally {
                Platform.runLater {
                    saving = false
                    menuTable.isDisable = false
                }
            }
        }
    }

    override fun dispose() = ioScope.cancel()

    private fun showAlert(title: String, message: String) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }
}
