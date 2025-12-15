package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.models.MenuItem
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

    @FXML
    fun initialize() {

        var allMenuItems = FXCollections.observableArrayList<MenuItem>()

        try {
            // --- Load menu items (fast) ---
            allMenuItems = FXCollections.observableArrayList(MenuItemRepository.getAllMenuItems())
            filteredMenu = FilteredList(allMenuItems) { true }
            menuList.items = filteredMenu


        } catch (e: Exception) {
            Logger.logError(e, "Error while loading menu items in OrderController.initialize()")
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

        // Search filter
        searchField.textProperty().addListener { _, _, newValue ->
            filteredMenu.setPredicate { item ->
                newValue.isNullOrBlank() || item.name.contains(newValue, ignoreCase = true)
            }
        }
        chargesField.textProperty().addListener { _, _, _ -> refreshOrder() }

        // Category filter
        val categories = listOf("All") + allMenuItems.map { it.category }.distinct()
        categoryFilter.items = FXCollections.observableArrayList(categories)
        categoryFilter.selectionModel.selectFirst()
        categoryFilter.setOnAction {
            val selected = categoryFilter.value
            filteredMenu.setPredicate { item ->
                (selected == "All" || item.category == selected) &&
                        (searchField.text.isNullOrBlank() || item.name.contains(searchField.text, ignoreCase = true))
            }
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
            filteredMenu.setPredicate { true }
        }

        // Setup order table
        orderTable.items = orderRows
        colItem.setCellValueFactory { SimpleStringProperty(it.value.name) }
        colQty.setCellValueFactory { SimpleIntegerProperty(it.value.quantity) }
        colPrice.setCellValueFactory { SimpleIntegerProperty(it.value.totalPrice) }

        colActions.setCellFactory {
            object : TableCell<OrderRow, String>() {
                private val plusBtn = Button("+").apply { styleClass.add("btn-success") }
                private val minusBtn = Button("-").apply { styleClass.add("btn-success") }
                private val removeBtn = Button("x").apply { styleClass.add("btn-success") }
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

    private fun refreshOrder() {
        orderTable.refresh()
        val subtotal = orderRows.sumOf { it.totalPrice }
        val orderType = orderTypeCombo.value
        val extraCharges = when (orderType) {
            "Delivery" -> chargesField.text.toIntOrNull() ?: 0
            "Service" -> chargesField.text.toIntOrNull() ?: 0
            else -> 0
        }
        val total = subtotal + extraCharges
        subtotalLabel.text = "Subtotal: Rs. $subtotal"
        totalLabel.text = "Total: Rs. $total"
    }

    @FXML
    private fun onSaveOrderClick() {
        ioScope.launch {
            try {
                if (orderRows.isEmpty()) {
                    Platform.runLater {
                        showAlert("No items in order", "Please add at least one item before saving.")
                    }
                    return@launch
                }

                val orderType = orderTypeCombo.value
                val deliveryCharges = if (orderType == "Delivery") chargesField.text.toIntOrNull() ?: 0 else 0
                val serviceCharges = if (orderType == "Service") chargesField.text.toIntOrNull() ?: 0 else 0
                val customerPhone = if (orderType == "Delivery") phoneField.text else null
                val customerAddress = if (orderType == "Delivery") addressField.text else null

                val subtotal = orderRows.sumOf { it.totalPrice }
                val total = subtotal + deliveryCharges + serviceCharges

                var savedOrder: Order? = null
                var isOldOrder = false

                withContext(Dispatchers.IO) {
                    try {
                        if (currentOrder != null) {
                            val updatedOrder = currentOrder!!.copy(
                                total = total,
                                orderType = orderType,
                                deliveryCharges = deliveryCharges,
                                serviceCharges = serviceCharges,
                                isEdited = true,
                                customerPhone = customerPhone,
                                customerAddress = customerAddress
                            )


                            // Restore inventory for previous items
                            currentOrder!!.items.forEach { oldItem ->
                                val recipes = recipeRepository.getRecipesForMenuItem(oldItem.menuItemId)
                                recipes.forEach { recipe ->
                                    val qtyToRestore = recipe.quantityNeeded * oldItem.quantity
                                    inventoryRepo.adjustStock(recipe.ingredientId, qtyToRestore)
                                }
                            }

                            orderRepository.updateOrder(updatedOrder)
                            orderRepository.deleteOrderItems(updatedOrder.id)

                            val items = orderRows.map {
                                OrderItem(
                                    id = 0,
                                    orderId = updatedOrder.id,
                                    menuItemId = it.menuItem.id ?: 0,
                                    itemName = it.menuItem.name,
                                    category = it.menuItem.category,
                                    size = it.menuItem.size,
                                    price = it.menuItem.price,
                                    quantity = it.quantity
                                )
                            }

                            items.forEach { orderRepository.insertOrderItem(it) }

                            // Deduct inventory again
                            for (row in orderRows) {
                                val recipes = recipeRepository.getRecipesForMenuItem(row.menuItem.id!!)
                                recipes.forEach { recipe ->
                                    val totalIngredientQty = recipe.quantityNeeded * row.quantity
                                    deductInventory(recipe.ingredientId, totalIngredientQty)
                                }
                            }

                            // Fetch updated order safely
                            val fetched = orderRepository.getOrderById(updatedOrder.id)
                            if (fetched == null) {
                                Logger.logError(Exception("Order not found after update!"), "Order ID=${updatedOrder.id}")
                            } else {
                                savedOrder = fetched
                                isOldOrder = true
                            }

                        } else {
                            val nextOrderNo = orderRepository.getNextOrderNo()
                            val orderId = orderRepository.insertOrder(
                                Order(
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
                            )

                            val items = orderRows.map {
                                OrderItem(
                                    id = 0,
                                    orderId = orderId,
                                    menuItemId = it.menuItem.id ?: 0,
                                    itemName = it.menuItem.name,
                                    category = it.menuItem.category,
                                    size = it.menuItem.size,
                                    price = it.menuItem.price,
                                    quantity = it.quantity
                                )
                            }

                            items.forEach { orderRepository.insertOrderItem(it) }

                            // Deduct inventory
                            for (row in orderRows) {
                                val recipes = recipeRepository.getRecipesForMenuItem(row.menuItem.id!!)
                                recipes.forEach { recipe ->
                                    val totalIngredientQty = recipe.quantityNeeded * row.quantity
                                    deductInventory(recipe.ingredientId, totalIngredientQty)
                                }
                            }

                            val fetched = orderRepository.getOrderById(orderId)
                            if (fetched == null) {
                                Logger.logError(Exception("Order not found after insert!"), "Order ID=$orderId")
                            } else {
                                savedOrder = fetched
                            }
                        }
                    } catch (e: Exception) {
                        Logger.logError(e, "Error in order saving block")
                    }
                }

                // Only continue if order was actually saved
                if (savedOrder == null) {
                    Platform.runLater {
                        showAlert("Error", "Failed to save order: Database returned null.")
                    }
                    return@launch
                }

                // Printing safely
                if (printReceiptCheck.isSelected) {
                    launch(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(7000) {
                                receiptPrinter.printReceipt("Black Copper BC-85AC", savedOrder!!, isOldOrder)
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
        printReceiptCheck.isSelected = true
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
}

object OrderEvents {
    private val listeners = mutableListOf<() -> Unit>()
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun notifyOrderAdded() { listeners.forEach { it.invoke() } }
}
