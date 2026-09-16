package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.models.MenuItem
import com.innovatewithomer.juiceforu.AppSettings
import com.innovatewithomer.juiceforu.PrinterService
import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.models.OrderItem
import com.innovatewithomer.juiceforu.repo.InventoryRepository
import com.innovatewithomer.juiceforu.repo.MenuItemRepository
import com.innovatewithomer.juiceforu.repo.OrderRepository
import com.innovatewithomer.juiceforu.repo.RecipeRepository
import com.innovatewithomer.juiceforu.utils.DisposableController
import com.innovatewithomer.juiceforu.utils.Logger
import com.innovatewithomer.juiceforu.utils.ReceiptPrinter
import javafx.application.Platform
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.stage.Stage
import kotlinx.coroutines.*

class OrderController: DisposableController {

    @FXML private lateinit var searchField: TextField
    @FXML private lateinit var categoryFilter: ComboBox<String>
    @FXML private lateinit var menuList: ListView<MenuItem>

    @FXML private lateinit var orderTable: TableView<OrderRow>
    @FXML private lateinit var colItem: TableColumn<OrderRow, String>
    @FXML private lateinit var colQty: TableColumn<OrderRow, Number>
    @FXML private lateinit var colPrice: TableColumn<OrderRow, Number>
    @FXML private lateinit var colActions: TableColumn<OrderRow, String>

    @FXML private lateinit var subtotalLabel: Label
    @FXML private lateinit var totalLabel: Label

    @FXML private lateinit var orderTypeCombo: ComboBox<String>
    @FXML private lateinit var chargesField: TextField
    @FXML private lateinit var orderIdLabel: Label

    @FXML private lateinit var phoneField: TextField
    @FXML private lateinit var addressField: TextField

    @FXML private lateinit var printReceiptCheck: CheckBox
    @FXML private lateinit var saveOrderButton: Button

    private val orderRepository = OrderRepository()
    private val recipeRepository = RecipeRepository()
    private val inventoryRepo = InventoryRepository()
    private val receiptPrinter = ReceiptPrinter()

    // Coroutine scope dedicated to IO/database work for this controller
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var editStage: Stage? = null

    private var currentOrder: Order? = null
    private val orderRows = FXCollections.observableArrayList<OrderRow>()
    private lateinit var filteredMenu: FilteredList<MenuItem>
    private var printerChoiceVersion = 0

    @FXML
    fun initialize() {
        val allMenuItems = FXCollections.observableArrayList<MenuItem>()
        filteredMenu = FilteredList(allMenuItems) { true }
        menuList.items = filteredMenu
        menuList.placeholder = Label("Loading menu…")
        menuList.isDisable = true
        categoryFilter.items = FXCollections.observableArrayList("All")
        categoryFilter.selectionModel.selectFirst()
        ioScope.launch {
            try {
                val items = MenuItemRepository.getAllMenuItems()
                Platform.runLater {
                    allMenuItems.setAll(items)
                    categoryFilter.items.setAll(listOf("All") + items.map { it.category }.distinct())
                    categoryFilter.selectionModel.selectFirst()
                    menuList.placeholder = Label("No menu items found")
                    menuList.isDisable = false
                    applyMenuFilter()
                }
            } catch (e: Exception) {
                Logger.logError(e, "Error while loading menu items in OrderController.initialize()")
                Platform.runLater { menuList.placeholder = Label("Could not load menu items") }
            }
        }

        // --- Show preview of next order number safely ---
        ioScope.launch {
            try {
                val nextOrderNo = orderRepository.peekNextOrderNo()

                Platform.runLater {
                    orderIdLabel.text = "Order #$nextOrderNo"
                }

            } catch (e: Exception) {
                Logger.logError(e, "Error while fetching next order number (peekNextOrderNo)")
            }
        }

        chargesField.isDisable = false
        phoneField.isDisable = true
        addressField.isDisable = true

        // Search and category filters share one predicate so neither can erase the other.
        searchField.textProperty().addListener { _, _, _ -> applyMenuFilter() }
        chargesField.textProperty().addListener { _, _, _ -> refreshOrder() }
        chargesField.textFormatter = TextFormatter<String> { change ->
            if (change.controlNewText.matches(Regex("\\d{0,7}"))) change else null
        }
        phoneField.textFormatter = TextFormatter<String> { change ->
            if (change.controlNewText.matches(Regex("[+0-9() -]{0,24}"))) change else null
        }

        // Category filter
        categoryFilter.setOnAction {
            applyMenuFilter()
        }

        // When clicking on menu → add to order
        menuList.setCellFactory {
            object : ListCell<MenuItem>() {
                override fun updateItem(item: MenuItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else "${item.name} (${item.size}) - Rs. ${item.price}"
                }
            }
        }
        menuList.setOnMouseClicked {
            val selected = menuList.selectionModel.selectedItem ?: return@setOnMouseClicked
            addItemToOrder(selected)
            searchField.clear()
            applyMenuFilter()
        }

        // Setup order table
        orderTable.items = orderRows
        colItem.setCellValueFactory { SimpleStringProperty(it.value.name) }
        colQty.setCellValueFactory { SimpleIntegerProperty(it.value.quantity) }
        colPrice.setCellValueFactory { SimpleIntegerProperty(it.value.totalPrice) }

        colActions.setCellFactory {
            object : TableCell<OrderRow, String>() {
                private val plusBtn = Button("+").apply { styleClass.add("quantity-button") }
                private val minusBtn = Button("−").apply { styleClass.add("quantity-button") }
                private val removeBtn = Button("Remove").apply { styleClass.add("btn-danger") }
                private val box = HBox(5.0, minusBtn, plusBtn, removeBtn)

                init {
                    plusBtn.setOnAction {
                        val row = tableRow.item as? OrderRow ?: return@setOnAction
                        row.quantity++
                        refreshOrder()
                    }
                    minusBtn.setOnAction {
                        val row = tableRow.item as? OrderRow ?: return@setOnAction
                        if (row.quantity > 1) row.quantity-- else orderRows.remove(row)
                        refreshOrder()
                    }
                    removeBtn.setOnAction {
                        val row = tableRow.item as? OrderRow ?: return@setOnAction
                        orderRows.remove(row)
                        refreshOrder()
                    }
                }

                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    graphic = if (empty) null else box
                }
            }
        }

        orderTypeCombo.items = FXCollections.observableArrayList("Service", "Takeaway", "Delivery")
        orderTypeCombo.selectionModel.selectFirst()
        printReceiptCheck.setOnAction { printerChoiceVersion++ }
        updateDefaultPrinterSelection()
        orderTypeCombo.valueProperty().addListener { _, _, newValue ->
            when (newValue) {
                "Delivery" -> {
                    chargesField.isDisable = false
                    phoneField.isDisable = false
                    addressField.isDisable = false
                }
                "Service" -> {
                    chargesField.isDisable = false
                    phoneField.isDisable = true
                    addressField.isDisable = true
                    phoneField.clear()
                    addressField.clear()
                }
                else -> { // Takeaway
                    chargesField.isDisable = true
                    chargesField.clear()
                    phoneField.isDisable = true
                    addressField.isDisable = true
                    phoneField.clear()
                    addressField.clear()
                }
            }
        }

        refreshOrder()
    }

    private fun addItemToOrder(menuItem: MenuItem) {
        val existing = orderRows.find { it.menuItem.id == menuItem.id }
        if (existing != null) existing.quantity++ else orderRows.add(OrderRow(menuItem, 1))
        refreshOrder()
    }

    private fun applyMenuFilter() {
        if (!::filteredMenu.isInitialized) return
        val query = searchField.text.orEmpty().trim()
        val category = categoryFilter.value ?: "All"
        filteredMenu.setPredicate { item ->
            (category == "All" || item.category == category) &&
                (query.isBlank() || item.name.contains(query, ignoreCase = true) ||
                    item.category.contains(query, ignoreCase = true))
        }
    }

    private fun refreshOrder() {
        orderTable.refresh()
        val subtotal = orderRows.sumOf { it.totalPrice }
        val orderType = orderTypeCombo.value
        val extraCharges = when (orderType) {
            "Delivery" -> chargesField.text.toIntOrNull() ?: 0
            "Service" -> chargesField.text.toIntOrNull() ?: 0
            else -> 0
        }
        val beforeDiscount = subtotal + extraCharges
        val total = (beforeDiscount * (1.0 - (currentOrder?.discountPercent ?: 0.0) / 100.0)).toInt()
        subtotalLabel.text = "Subtotal: Rs. $subtotal"
        totalLabel.text = "Total: Rs. $total"
    }

    @FXML
    private fun onSaveOrderClick() {
        if (orderRows.isEmpty()) {
            showAlert("No items in order", "Please add at least one item before saving.")
            return
        }
        val orderType = orderTypeCombo.value
        val charges = chargesField.text.toIntOrNull() ?: 0
        if (orderType == "Delivery" && (phoneField.text.isNullOrBlank() || addressField.text.isNullOrBlank())) {
            showAlert("Delivery details required", "Enter both a customer phone number and delivery address.")
            return
        }
        val configuredPrinter = AppSettings.printerName
        val rowsToSave = orderRows.map { it.copy() }
        val orderToEdit = currentOrder
        val printAfterSave = printReceiptCheck.isSelected
        val chargesAmount = charges
        val customerPhone = if (orderType == "Delivery") phoneField.text else null
        val customerAddress = if (orderType == "Delivery") addressField.text else null
        saveOrderButton.isDisable = true
        saveOrderButton.text = "Saving…"
        ioScope.launch {
            try {
                val selectedPrinter = if (printAfterSave) configuredPrinter ?: PrinterService.defaultPrinterName() else null
                if (printAfterSave && selectedPrinter.isNullOrBlank()) {
                    Platform.runLater {
                        showAlert("Printer not configured", "Select a receipt printer in Settings, or turn off Print receipt for this order.")
                    }
                    return@launch
                }
                val deliveryCharges = if (orderType == "Delivery") chargesAmount else 0
                val serviceCharges = if (orderType == "Service") chargesAmount else 0

                val subtotal = rowsToSave.sumOf { it.totalPrice }
                val beforeDiscount = subtotal + deliveryCharges + serviceCharges
                val discountPercent = orderToEdit?.discountPercent ?: 0.0
                val discountAmount = beforeDiscount * discountPercent / 100.0
                val total = (beforeDiscount - discountAmount).toInt()

                var savedOrder: Order? = null
                var isOldOrder = false

                // Prepare items
                val items = rowsToSave.map {
                    OrderItem(
                        id = 0,
                        orderId = orderToEdit?.id ?: 0,
                        menuItemId = it.menuItem.id ?: 0,
                        itemName = it.menuItem.name,
                        category = it.menuItem.category,
                        size = it.menuItem.size,
                        price = it.menuItem.price,
                        quantity = it.quantity
                    )
                }

                // Compute inventory deductions from recipes
                val deductions = mutableListOf<Pair<Int, Double>>()
                for (row in rowsToSave) {
                    val menuItemId = row.menuItem.id ?: 0
                    if (menuItemId > 0) {
                        val recipes = recipeRepository.getRecipesForMenuItem(menuItemId)
                        for (recipe in recipes) {
                            deductions.add(recipe.ingredientId to (recipe.quantityNeeded * row.quantity))
                        }
                    }
                }

                if (orderToEdit != null) {
                    val updatedOrder = orderToEdit.copy(
                        total = total,
                        discountAmount = discountAmount,
                        orderType = orderType,
                        deliveryCharges = deliveryCharges,
                        serviceCharges = serviceCharges,
                        isEdited = true,
                        customerPhone = customerPhone,
                        customerAddress = customerAddress
                    )

                    // Compute inventory to restore from previous items
                    val restores = mutableListOf<Pair<Int, Double>>()
                    orderToEdit.items.forEach { oldItem ->
                        if (oldItem.menuItemId > 0) {
                            val recipes = recipeRepository.getRecipesForMenuItem(oldItem.menuItemId)
                            for (recipe in recipes) {
                                restores.add(recipe.ingredientId to (recipe.quantityNeeded * oldItem.quantity))
                            }
                        }
                    }

                    savedOrder = orderRepository.updateOrderAtomic(updatedOrder, items, restores, deductions)
                    isOldOrder = true
                } else {
                    val nextOrderNo = orderRepository.getNextOrderNo()
                    val newOrder = Order(
                        id = 0,
                        orderNo = nextOrderNo,
                        total = total,
                        orderType = orderType,
                        createdAt = System.currentTimeMillis(),
                        deliveryCharges = deliveryCharges,
                        serviceCharges = serviceCharges,
                        isEdited = false,
                        customerPhone = customerPhone,
                        customerAddress = customerAddress
                    )
                    savedOrder = orderRepository.saveOrderAtomic(newOrder, items, deductions)
                }

                // Printing safely
                if (printAfterSave) {
                    launch(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(7000) {
                                receiptPrinter.printReceipt(selectedPrinter!!, savedOrder, isOldOrder)
                            }
                        } catch (ex: Exception) {
                            Logger.logError(ex, "Printing failed")
                        }
                    }
                }

                // Update UI
                Platform.runLater {
                    showAlert("Success", "Order saved successfully!")
                    editStage?.close()
                    orderRows.clear()
                    refreshOrder()
                    startNewOrder()
                    OrderEvents.notifyOrderAdded()
                }

            } catch (ex: Exception) {
                ex.printStackTrace()
                Logger.logError(ex, "Error in onSaveOrderClick()")
                Platform.runLater {
                    showAlert("Error", "Failed to save order: ${ex.message ?: ex.toString()}")
                }
            } finally {
                Platform.runLater {
                    saveOrderButton.isDisable = false
                    saveOrderButton.text = "Save order"
                }
            }
        }
    }


    fun startNewOrder() {
        orderRows.clear()
        refreshOrder()

        // reset UI defaults
        orderTypeCombo.selectionModel.selectFirst()
        chargesField.clear()
        chargesField.isDisable = true
        updateDefaultPrinterSelection()
        printReceiptCheck.isDisable = false

        // fetch next order no (non-mutating) in background
        ioScope.launch {
            try {
                val nextOrderNo = orderRepository.peekNextOrderNo()
                Platform.runLater {
                    orderIdLabel.text = "Order #$nextOrderNo"
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                Logger.logError(ex, ex.message)
                Platform.runLater {
                    orderIdLabel.text = "Order #N/A"
                }
            }
        }

        currentOrder = null
    }

    private fun showAlert(title: String, message: String) {
        // always call on UI thread (we ensure callers use Platform.runLater or call from UI thread)
        val alert = Alert(Alert.AlertType.WARNING)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    // helper row model
    data class OrderRow(val menuItem: MenuItem, var quantity: Int) {
        val name: String get() = "${menuItem.name} (${menuItem.size})"
        val totalPrice: Int get() = (menuItem.price * quantity).toInt()
    }

    fun deductInventory(ingredientId: Int, quantity: Double) {
        // inventoryRepo does DB writes — ensure it's called from IO. Our callers are on IO scope, so fine.
        val item = inventoryRepo.getItemById(ingredientId)
        if (item != null && item.quantity >= quantity) {
            inventoryRepo.adjustStock(ingredientId, -quantity)
        } else {
            println("⚠️ Not enough stock for ${item?.name ?: "Unknown ingredient"}")
        }
    }

    fun loadOrderForEdit(order: Order) {
        // This is typically called from main thread. Populate UI and prepare data.
        this.currentOrder = order
        orderRows.clear()

        order.items.forEach { item ->
            orderRows.add(
                OrderRow(
                    menuItem = MenuItem(
                        id = item.menuItemId,
                        name = item.itemName,
                        price = item.price,
                        size = item.size,
                        category = item.category
                    ),
                    quantity = item.quantity
                )
            )
        }

        orderTypeCombo.value = order.orderType
        chargesField.text = when (order.orderType) {
            "Delivery" -> order.deliveryCharges.toString()
            "Service" -> order.serviceCharges.toString()
            else -> ""
        }
        phoneField.text = order.customerPhone
        addressField.text = order.customerAddress

        printReceiptCheck.isDisable = false
        printReceiptCheck.isSelected = false
        printReceiptCheck.tooltip = Tooltip("Enable if you want to reprint the edited order.")

        refreshOrder()
    }

    // call this when controller/window is disposed to cancel IO coroutines
    override fun dispose() {
        ioScope.cancel()
    }

    private fun updateDefaultPrinterSelection() {
        val version = ++printerChoiceVersion
        val configured = !AppSettings.printerName.isNullOrBlank()
        printReceiptCheck.isSelected = configured
        if (!configured) {
            ioScope.launch {
                val available = runCatching { PrinterService.defaultPrinterName() != null }.getOrDefault(false)
                Platform.runLater {
                    if (version == printerChoiceVersion && !printReceiptCheck.isSelected && available && currentOrder == null) {
                        printReceiptCheck.isSelected = true
                    }
                }
            }
        }
    }
}

object OrderEvents {
    private val listeners = mutableListOf<() -> Unit>()
    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
    fun notifyOrderAdded() { listeners.toList().forEach { it.invoke() } }
}
