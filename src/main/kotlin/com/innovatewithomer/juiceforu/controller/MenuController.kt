package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.models.MenuItem
import com.innovatewithomer.juiceforu.repo.MenuItemRepository
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox

class MenuController {

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

        // load data
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

                val editBtn = Button("Edit").apply {
                    setOnAction {
                        val item = tableRow.item
                        if (item != null) {
                            val dialog = TextInputDialog(item.name).apply {
                                title = "Edit Menu Item"
                                headerText = "Edit details for ${item.name}"
                                contentText = "Enter new name:"
                            }

                            val newName = dialog.showAndWait()
                            if (newName.isPresent && newName.get().isNotBlank()) {
                                // update item fields (for now only name, but you can extend for all fields)
                                val updatedItem = item.copy(name = newName.get())

                                // save to DB
                                MenuItemRepository.updateMenuItem(updatedItem)

                                // refresh UI
                                refreshTable()
                            }
                        }
                    }
                }


                init {
                    btnEdit.setOnAction {
                        val item = tableView.items[index]
                        onEditItem(item)
                    }
                    btnDelete.setOnAction {
                        val item = tableView.items[index]
                        onDeleteItem(item)
                    }

                    val pane = HBox(10.0, btnEdit, btnDelete)
                    pane.style = "-fx-alignment: CENTER;"
                    graphic = pane
                }

                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    graphic = if (empty) null else graphic
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
                val price = priceField.text.toDoubleOrNull() ?: item.price
                item.copy(
                    category = categoryField.text,
                    name = nameField.text,
                    size = sizeField.text,
                    price = price
                )
            } else null
        }

        val result = dialog.showAndWait()
        if (result.isPresent) {
            MenuItemRepository.updateMenuItem(result.get())
            refreshTable()
        }
    }



    private fun onDeleteItem(item: MenuItem) {
        val confirm = Alert(Alert.AlertType.CONFIRMATION)
        confirm.title = "Delete Confirmation"
        confirm.headerText = null
        confirm.contentText = "Are you sure you want to delete '${item.name}'?"

        val result = confirm.showAndWait()
        if (result.isPresent && result.get() == ButtonType.OK) {
            MenuItemRepository.deleteMenuItem(item.id!!)
            refreshTable()
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
        if (price == null) {
            showAlert("Error", "Price must be a number.")
            return
        }

        val newItem = MenuItem(
            id = 0, // will be auto-generated by DB
            category = category,
            name = name,
            size = size,
            price = price
        )

        // insert into DB
        MenuItemRepository.addMenuItem(newItem)

        // ✅ refresh table
        refreshTable()

        // clear fields
        txtCategory.clear()
        txtName.clear()
        txtSize.clear()
        txtPrice.clear()
    }

    private fun refreshTable() {
        menuItems.setAll(MenuItemRepository.getAllMenuItems())
        menuTable.items = menuItems
        addActionButtons()
    }

    private fun showAlert(title: String, message: String) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }
}
