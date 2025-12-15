package com.innovatewithomer.juiceforu.controller

import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.image.BitImageWrapper
import com.github.anastaciocintra.escpos.image.BitonalOrderedDither
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl
import com.github.anastaciocintra.escpos.image.EscPosImage
import com.github.anastaciocintra.output.PrinterOutputStream
import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.repo.OrderRepository
import com.innovatewithomer.juiceforu.utils.DisposableController
import com.innovatewithomer.juiceforu.utils.Logger
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.stage.Screen
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.Color
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ColorConvertOp
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO
import javax.print.PrintService

class OrderHistoryController : DisposableController {

    @FXML private lateinit var ordersTable: TableView<Order>
    @FXML private lateinit var idColumn: TableColumn<Order, Int>
    @FXML private lateinit var itemsColumn: TableColumn<Order, String>
    @FXML private lateinit var totalColumn: TableColumn<Order, Int>
    @FXML private lateinit var dateColumn: TableColumn<Order, String>
    @FXML private lateinit var orderTypeColumn: TableColumn<Order, String>
    @FXML private lateinit var actionsColumn: TableColumn<Order, Void>
    @FXML private lateinit var statusColumn: TableColumn<Order, String>

    @FXML private lateinit var todaySalesLabel: Label
    @FXML private lateinit var weeklySalesLabel: Label
    @FXML private lateinit var monthlySalesLabel: Label

    // Dedicated scope for IO work; cancel when controller is disposed
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo = OrderRepository()

    @FXML
    fun initialize() {
        idColumn.cellValueFactory = PropertyValueFactory("orderNo")
        totalColumn.cellValueFactory = PropertyValueFactory("total")


        // Row factory: nice highlight and stable toggle selection
        ordersTable.setRowFactory {
            val row = TableRow<Order>()
            var lastSelected = false

            row.selectedProperty().addListener { _, _, selected ->
                if (selected) {
                    row.style = "-fx-background-color: #0078d7; -fx-text-fill: white;"
                } else {
                    row.style = ""
                }
            }

            row.setOnMouseClicked {
                if (!row.isEmpty) {
                    val index = row.index
                    val selectionModel = ordersTable.selectionModel

                    Platform.runLater {
                        if (selectionModel.isSelected(index)) {
                            if (lastSelected) {
                                selectionModel.clearSelection()
                                lastSelected = false
                            } else {
                                lastSelected = true
                            }
                        } else {
                            lastSelected = false
                        }
                    }
                }
            }
            row
        }

        // Total cell formatting (shows discount tag & tooltip)
        totalColumn.setCellFactory {
            object : TableCell<Order, Int>() {
                private val label = Label().apply { style = "-fx-font-size: 13px;" }

                override fun updateItem(item: Int?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        graphic = null
                        tooltip = null
                    } else {
                        // Use safe access to tableView items
                        val idx = index
                        if (idx >= 0 && idx < tableView.items.size) {
                            val order = tableView.items[idx]
                            if (order.discountPercent > 0.0) {
                                // show discounted value (order.total should already be the final amount)
                                label.text = "Rs. ${"%,d".format(order.total)}  (−${order.discountPercent.toInt()}%)"
                                label.style = "-fx-text-fill: #007700; -fx-font-weight: bold;"
                                tooltip = Tooltip("Discounted from Rs. ${"%,d".format((order.total + order.discountAmount).toInt())}")
                            } else {
                                label.text = "Rs. ${"%,d".format(order.total)}"
                                label.style = "-fx-text-fill: black;"
                                tooltip = null
                            }
                            graphic = label
                        } else {
                            text = item.toString()
                            graphic = null
                        }
                    }
                }
            }
        }

        // Date formatting
        dateColumn.setCellValueFactory { cellData ->
            val millis = cellData.value.createdAt
            val formatted = try {
                val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a"))
            } catch (e: Exception) {
                Logger.logError(e, e.message)
                millis.toString()
            }
            SimpleStringProperty(formatted)
        }

        orderTypeColumn.setCellValueFactory { cellData -> SimpleStringProperty(cellData.value.orderType) }

        itemsColumn.setCellValueFactory { cellData ->
            val order = cellData.value
            val itemsText = order.items.joinToString("\n") { "${it.itemName} (${it.size}) x${it.quantity}" }
            SimpleStringProperty(itemsText)
        }

        itemsColumn.setCellFactory {
            object : TableCell<Order, String>() {
                private val label = Label().apply { isWrapText = true }
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                    } else {
                        label.text = item
                        label.maxWidth = itemsColumn.width - 10
                        graphic = label
                    }
                }
            }
        }

        // Actions column (print & edit). printing happens in IO scope (so UI isn't blocked)
        actionsColumn.setCellFactory {
            object : TableCell<Order, Void>() {
                private val printBtn = Button().apply {
                    styleClass.add("btn-icon")
                    graphic = loadIcon("/com/innovatewithomer/juiceforu/icons/printer.png")
                    setOnAction {
                        val idx = index
                        if (idx >= 0 && idx < tableView.items.size) {
                            val order = tableView.items[idx]
                            // print on IO thread
                            ioScope.launch {
                                try {
                                    printReceipt("Black Copper BC-85AC", order)
                                    // if you want a UI confirmation when done:
                                    Platform.runLater {
                                        showAlert("Receipt Printed", "Order #${order.orderNo} receipt sent to printer.")
                                    }
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                    Logger.logError(ex, ex.message)
                                    Platform.runLater {
                                        showAlert("Error", "Failed to print: ${ex.message}")
                                    }
                                }
                            }
                        }
                    }
                }

                private val editBtn = Button().apply {
                    styleClass.add("btn-icon")
                    graphic = loadIcon("/com/innovatewithomer/juiceforu/icons/tool.png")
                    setOnAction {
                        val idx = index
                        if (idx >= 0 && idx < tableView.items.size) {
                            val order = tableView.items[idx]
                            openOrderForEdit(order)
                        }
                    }
                }

                private val container = HBox(5.0, printBtn, editBtn).apply { alignment = Pos.CENTER }

                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    graphic = if (empty) null else container
                }
            }
        }

        // Status dropdown column
        statusColumn.setCellValueFactory { SimpleStringProperty(it.value.orderStatus ?: "PENDING") }
        statusColumn.setCellFactory {
            object : TableCell<Order, String>() {
                private val comboBox = ComboBox<String>().apply {
                    items = FXCollections.observableArrayList("PENDING", "COMPLETED", "CANCELLED")
                    prefWidth = 110.0
                    style = "-fx-font-size: 13px;"
                    setOnAction {
                        val idx = index
                        if (idx >= 0 && idx < tableView.items.size) {
                            val order = tableView.items[idx]
                            val newStatus = value
                            order.orderStatus = newStatus
                            // persist change on IO
                            ioScope.launch {
                                try {
                                    repo.updateOrderStatus(order.id, newStatus)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Logger.logError(e, e.message)
                                    Platform.runLater { showAlert("Error", "Failed to update status: ${e.message}") }
                                }
                            }
                        }
                    }
                }

                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                    } else {
                        comboBox.value = item
                        graphic = comboBox
                    }
                }
            }
        }

        // Event listeners to reload
        OrderEvents.addListener {
            onLoadOrdersClick()
            loadSalesSummary()
        }

        // initial load
        onLoadOrdersClick()
        loadSalesSummary()


        // small UX: hide or mask some labels until unlocked
        weeklySalesLabel.text = "Weekly: ****"
        monthlySalesLabel.text = "Monthly: ****"
        weeklySalesLabel.setOnMouseClicked { handleSalesUnlock("weekly") }
        monthlySalesLabel.setOnMouseClicked { handleSalesUnlock("monthly") }
    }

    /**
     * Apply discount — safe: DB update on IO thread, UI changes via Platform.runLater
     */
    @FXML
    fun onApplyDiscountClick() {
        val selectedOrder = ordersTable.selectionModel.selectedItem
        if (selectedOrder == null) {
            showAlert("No Order Selected", "Please select an order first.")
            return
        }

        val dialog = TextInputDialog().apply {
            title = "Apply Discount"
            headerText = "Enter discount percentage (e.g., 10 for 10%)"
            contentText = "Discount (%):"
        }

        val result = dialog.showAndWait()
        if (result.isPresent) {
            try {
                val discountPercent = result.get().toDouble()
                if (discountPercent !in 0.0..100.0) {
                    showAlert("Invalid Value", "Please enter a valid discount between 0 and 100.")
                    return
                }

                val oldTotal = selectedOrder.total.toDouble()
                val discountAmount = oldTotal * discountPercent / 100.0
                val newTotal = oldTotal - discountAmount

                // run DB update in IO scope (suspend function in repo)
                ioScope.launch {
                    try {
                        repo.updateOrderDiscountAndTotal(
                            selectedOrder.id,
                            discountPercent,
                            discountAmount,
                            newTotal
                        )

                        // now update UI on FX thread
                        Platform.runLater {
                            selectedOrder.discountPercent = discountPercent
                            selectedOrder.discountAmount = discountAmount
                            selectedOrder.total = newTotal.toInt()
                            ordersTable.refresh()
                            ordersTable.selectionModel.clearSelection()

                            showAlert(
                                "Discount Applied",
                                "A discount of ${discountPercent}% (Rs. ${"%,.2f".format(discountAmount)}) applied.\n" +
                                        "New Total: Rs. ${"%,.2f".format(newTotal)}"
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Logger.logError(e, e.message)
                        Platform.runLater {
                            showAlert("Error", "Failed to update discount: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.logError(e, e.message)
                showAlert("Error", "Failed to apply discount: ${e.message}")
            }
        }
    }

    // helper: load icon
    private fun loadIcon(path: String, size: Double = 18.0): ImageView {
        val url = javaClass.getResource(path)
            ?: throw IllegalStateException("❌ Icon not found: $path")
        return ImageView(Image(url.toExternalForm())).apply {
            fitWidth = size
            fitHeight = size
            isPreserveRatio = true
        }
    }

    private fun showAlert(title: String, message: String) {
        // Always run alerts on FX thread
        Platform.runLater {
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.title = title
            alert.headerText = null
            alert.contentText = message
            alert.showAndWait()
        }
    }

    @FXML
    fun onLoadOrdersClick() {
        ioScope.launch {
            try {
                // Step 1: Load today's orders first (fast)
                val todayOrders = repo.getOrdersByDate("today")

                // Step 2: Ensure orders are enriched with necessary details
                // (e.g., order items, customer info, etc.) to prevent NullPointerException
                val enrichedOrders = todayOrders.map { order ->
                    repo.getOrderById(order.id) ?: order
                }

                // Step 3: Show the enriched today's orders
                Platform.runLater {
                    ordersTable.items = FXCollections.observableArrayList(enrichedOrders)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Logger.logError(e, e.message)
                Platform.runLater {
                    showAlert("Error", "Failed to load today's orders: ${e.message}")
                }
            }
        }
    }


    private fun loadSalesSummary() {
        ioScope.launch {
            try {
                val summary = repo.getSalesSummary() // suspend
                Platform.runLater {
                    todaySalesLabel.text = "Today: Rs. ${summary["today"]?.toInt() ?: 0}"
                    // weekly/monthly remain masked until unlocked
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Logger.logError(e, e.message)
                Platform.runLater { showAlert("Error", "Failed to load sales summary: ${e.message}") }
            }
        }
    }

    @FXML
    fun onTodayFilter() {
        ioScope.launch {
            val orders = repo.getOrdersByDate("today")
            Platform.runLater { ordersTable.items = FXCollections.observableArrayList(orders) }
        }
    }

    @FXML
    fun onWeeklyFilter() {
        ioScope.launch {
            val orders = repo.getOrdersByDate("weekly")
            Platform.runLater { ordersTable.items = FXCollections.observableArrayList(orders) }
        }
    }

    @FXML
    fun onMonthlyFilter() {
        ioScope.launch {
            val orders = repo.getOrdersByDate("monthly")
            Platform.runLater { ordersTable.items = FXCollections.observableArrayList(orders) }
        }
    }

    private fun handleSalesUnlock(kind: String) {
        // Example: prompt for simple password or confirm; just reveal for now.
        val confirm = Alert(Alert.AlertType.CONFIRMATION)
        confirm.title = "Unlock"
        confirm.headerText = "Show $kind sales?"
        confirm.contentText = "Are you sure?"
        val res = confirm.showAndWait()
        if (res.isPresent && res.get() == ButtonType.OK) {
            ioScope.launch {
                try {
                    val summary = repo.getSalesSummary()
                    Platform.runLater {
                        if (kind == "weekly") weeklySalesLabel.text = "Weekly: Rs. ${summary["weekly"]?.toInt() ?: 0}"
                        if (kind == "monthly") monthlySalesLabel.text = "Monthly: Rs. ${summary["monthly"]?.toInt() ?: 0}"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Logger.logError(e, e.message)
                    Platform.runLater { showAlert("Error", "Failed to load sales summary: ${e.message}") }
                }
            }
        }
    }


//    private fun buildReceiptContent(orderId: Int, isOldOrder: Boolean): ByteArray {
//        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy hh:mm aa", Locale.getDefault())
//        val lineWidth = 48
//        val charset = Charset.forName("CP437")
//
//        val initialize = byteArrayOf(0x1B, 0x40)
//        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
//        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
//        val doubleHeightOn = byteArrayOf(0x1B, 0x21, 0x10)
//        val normalText = byteArrayOf(0x1B, 0x21, 0x00)
//        val centerAlign = byteArrayOf(0x1B, 0x61, 0x01)
//        val leftAlign = byteArrayOf(0x1B, 0x61, 0x00)
//        val cutPaper = byteArrayOf(0x1D, 0x56, 0x41, 0x10)
//
//        // --- Fetch order + items from DB ---
//        val orderRepo = OrderRepository()
//        val order = orderRepo.getOrderById(orderId) ?: run {
//            println("Order with ID $orderId not found!")
//            return ByteArray(0)
//        }
//        val items = orderRepo.getOrderItems(orderId)
//
//
//
//        println("===== ORDER ITEMS =====")
//        items.forEach { item ->
//            println("Item Name: ${item.itemName}, Size: ${item.size}, Qty: ${item.quantity}, Price: ${item.price}")
//        }
//
//        return ByteArrayOutputStream().apply {
//            write(initialize)
//
//            // --- Header ---
//            write(centerAlign)
//            write(doubleHeightOn)
//            write(boldOn)
//            write("MANDRA PIZZA HUT\n".toByteArray(charset))
//            write(normalText)
//            write(boldOff)
//            write("\n".toByteArray(charset))
//
//            // --- Contact Info ---
//            write(leftAlign)
//            write(boldOn)
//            write("Mandra Pizza Hut Near Thandi Sarak\nG.T Road Mandra\n".toByteArray(charset))
//            write("Tel: 051-3591155  WhatsApp: 0309-5107040\n".toByteArray(charset))
//            write(boldOff)
//            write("${"=".repeat(lineWidth)}\n".toByteArray(charset))
//            write("ORDER TIME: ${sdfDateTime.format(order.createdAt)}\n".toByteArray(charset))
//            write("ORDER TYPE: ${order.orderType}\n".toByteArray(charset))
//
//            write(boldOff)
//
//            if (order.orderType.equals("delivery", ignoreCase = true)) {
//                order.customerAddress?.takeIf { it.isNotBlank() && it.lowercase() != "null" }?.let {
//                    write("DELIVERY ADDRESS: ${it.uppercase()}\n".toByteArray(charset))
//                }
//                order.customerPhone?.takeIf { it.isNotBlank()}?.let {
//                    write("PHONE NUMBER: $it\n".toByteArray(charset))
//                }
//            }
//
//            // --- Items Table ---
//            val colItemWidth = 30
//            val colQtyWidth = 4
//            val colPriceWidth = 12
//
//            write(boldOn)
//            write("%-${colItemWidth}s %${colQtyWidth}s %${colPriceWidth}s\n".format("ITEM", "QTY", "PRICE").toByteArray(charset))
//            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))
//            write(boldOff)
//
//            var totalAmount = 0.0
//
//            for (item in items) {
//                val name = "${item.itemName.uppercase()} (${item.size.uppercase()})".take(colItemWidth)
//                val qty = "x${item.quantity}".take(colQtyWidth)
//                val price = item.price * item.quantity
//                totalAmount += price
//
//                val formattedPrice = "RS.${"%,.2f".format(price)}"
//                val line = "%-${colItemWidth}s %${colQtyWidth}s %${colPriceWidth}s\n"
//                    .format(name, qty, formattedPrice)
//                write(line.toByteArray(charset))
//            }
//
//// --- Totals ---
//            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))
//            write(boldOn)
//
//            val labelWidth = colItemWidth
//            val amountWidth = colPriceWidth
//
//            fun writeTotalLine(label: String, amount: String) {
//                val line = "%-${labelWidth}s %${colQtyWidth}s %${amountWidth}s\n"
//                    .format(label, "", amount)
//                write(line.toByteArray(charset))
//            }
//
//// ✅ Always calculate subtotal (sum of items)
//            val subtotal = totalAmount
//
//// ✅ Write subtotal
//            writeTotalLine("SUBTOTAL:", "RS.${"%,.2f".format(subtotal)}")
//
//// ✅ Add service or delivery charges
//            val servicePrice = order.serviceCharges.toDouble()
//            val deliveryPrice = order.deliveryCharges.toDouble()
//            if (order.orderType.equals("DELIVERY", true)) {
//                writeTotalLine("DELIVERY CHARGES:", "RS.${"%,.2f".format(deliveryPrice)}")
//            } else if (order.orderType.equals("SERVICE", true)) {
//                writeTotalLine("SERVICE CHARGES:", "RS.${"%,.2f".format(servicePrice)}")
//            }
//
//// ✅ Apply discount if present
//            var grandTotal = subtotal + servicePrice + deliveryPrice
//            if (order.discountPercent > 0) {
//                writeTotalLine(
//                    "DISCOUNT (${order.discountPercent.toInt()}%):",
//                    "-RS.${"%,.2f".format(order.discountAmount)}"
//                )
//                grandTotal -= order.discountAmount
//            }
//
//// ✅ Final total (always uses updated order.total if available)
//            val finalTotal = if (order.total > 0) order.total.toDouble() else grandTotal
//            writeTotalLine("TOTAL AMOUNT:", "RS.${"%,.2f".format(finalTotal)}")
//
//            write(boldOff)
//            write("${"=".repeat(lineWidth)}\n".toByteArray(charset))
//
//            // --- Footer ---
//            write(centerAlign)
//            write(boldOn)
//            write("THANKS FOR CHOOSING MANDRA PIZZA HUT\n\n".toByteArray(charset))
//            write("YOUR OPINION HELPS US IMPROVE\n".toByteArray(charset))
//            write("SHARE YOUR FEEDBACK!\n\n".toByteArray(charset))
//            write(boldOff)
//
//            write(cutPaper)
//        }.toByteArray()
//    }

    suspend fun buildReceiptContent(orderId: Int): ByteArray = withContext(Dispatchers.IO) {
        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy hh:mm aa", Locale.getDefault())
        val lineWidth = 48
        val charset = Charset.forName("CP437")

        val initialize = byteArrayOf(0x1B, 0x40)
        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
        val doubleHeightOn = byteArrayOf(0x1B, 0x21, 0x10)
        val normalText = byteArrayOf(0x1B, 0x21, 0x00)
        val centerAlign = byteArrayOf(0x1B, 0x61, 0x01)
        val leftAlign = byteArrayOf(0x1B, 0x61, 0x00)
        val cutPaper = byteArrayOf(0x1D, 0x56, 0x41, 0x10)

        // --- Fetch order + items from DB on IO thread ---
        val orderRepo = OrderRepository()
        val order = orderRepo.getOrderById(orderId) ?: return@withContext ByteArray(0)
        val items = orderRepo.getOrderItems(orderId)

        println("===== ORDER ITEMS =====")
        items.forEach { item ->
            println("Item Name: ${item.itemName}, Size: ${item.size}, Qty: ${item.quantity}, Price: ${item.price}")
        }

        // --- Build receipt entirely in memory ---
        val output = ByteArrayOutputStream()

        output.apply {
            write(initialize)

            // Header
            write(centerAlign)
            write(doubleHeightOn)
            write(boldOn)
            write("JUICE FOR U\n".toByteArray(charset))
            write(normalText)
            write(boldOff)
            write("\n".toByteArray(charset))

            // Contact Info
            write(leftAlign)
            write(boldOn)
            write("Juice for you Near Thandi Sarak\nG.T Road Mandra\n".toByteArray(charset))
            write("Tel: 051-3591155  WhatsApp: 0309-5107000\n".toByteArray(charset))
            write(boldOff)
            write("${"=".repeat(lineWidth)}\n".toByteArray(charset))
            write("ORDER TIME: ${sdfDateTime.format(order.createdAt)}\n".toByteArray(charset))
            write("ORDER TYPE: ${order.orderType}\n".toByteArray(charset))

            if (order.orderType.equals("delivery", ignoreCase = true)) {
                order.customerAddress?.takeIf { it.isNotBlank() && it.lowercase() != "null" }?.let {
                    write("DELIVERY ADDRESS: ${it.uppercase()}\n".toByteArray(charset))
                }
                order.customerPhone?.takeIf { it.isNotBlank() }?.let {
                    write("PHONE NUMBER: $it\n".toByteArray(charset))
                }
            }

            // Items Table
            val colItemWidth = 30
            val colQtyWidth = 4
            val colPriceWidth = 12

            write(boldOn)
            write("%-${colItemWidth}s %${colQtyWidth}s %${colPriceWidth}s\n".format("ITEM", "QTY", "PRICE").toByteArray(charset))
            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))
            write(boldOff)

            var totalAmount = 0.0
            for (item in items) {
                val name = "${item.itemName.uppercase()} (${item.size.uppercase()})".take(colItemWidth)
                val qty = "x${item.quantity}".take(colQtyWidth)
                val price = item.price * item.quantity
                totalAmount += price

                val formattedPrice = "RS.${"%,.2f".format(price)}"
                val line = "%-${colItemWidth}s %${colQtyWidth}s %${colPriceWidth}s\n"
                    .format(name, qty, formattedPrice)
                write(line.toByteArray(charset))
            }

            // Totals
            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))
            write(boldOn)

            val labelWidth = colItemWidth
            val amountWidth = colPriceWidth

            fun writeTotalLine(label: String, amount: String) {
                val line = "%-${labelWidth}s %${colQtyWidth}s %${amountWidth}s\n"
                    .format(label, "", amount)
                write(line.toByteArray(charset))
            }

            val subtotal = totalAmount
            writeTotalLine("SUBTOTAL:", "RS.${"%,.2f".format(subtotal)}")

            val servicePrice = order.serviceCharges.toDouble()
            val deliveryPrice = order.deliveryCharges.toDouble()

            if (order.orderType.equals("DELIVERY", true)) {
                writeTotalLine("DELIVERY CHARGES:", "RS.${"%,.2f".format(deliveryPrice)}")
            } else if (order.orderType.equals("SERVICE", true)) {
                writeTotalLine("SERVICE CHARGES:", "RS.${"%,.2f".format(servicePrice)}")
            }

            var grandTotal = subtotal + servicePrice + deliveryPrice
            if (order.discountPercent > 0) {
                writeTotalLine(
                    "DISCOUNT (${order.discountPercent.toInt()}%):",
                    "-RS.${"%,.2f".format(order.discountAmount)}"
                )
                grandTotal -= order.discountAmount
            }

            val finalTotal = if (order.total > 0) order.total.toDouble() else grandTotal
            writeTotalLine("TOTAL AMOUNT:", "RS.${"%,.2f".format(finalTotal)}")

            write(boldOff)
            write("${"=".repeat(lineWidth)}\n".toByteArray(charset))

            // Footer
            write(centerAlign)
            write(boldOn)
            write("THANKS FOR CHOOSING JUICE FOR YOU\n\n".toByteArray(charset))
            write("YOUR OPINION HELPS US IMPROVE\n".toByteArray(charset))
            write("SHARE YOUR FEEDBACK!\n\n".toByteArray(charset))
            write(boldOff)
            write(cutPaper)
        }

        output.toByteArray()
    }



    private fun openOrderForEdit(order: Order) {
        try {
            // ✅ Reset status to PENDING if edited after completion
            if (order.orderStatus.equals("COMPLETED", ignoreCase = true)) {
                order.orderStatus = "PENDING"
                OrderRepository().updateOrderStatus(order.id, "PENDING")
            }

            val loader = FXMLLoader(javaClass.getResource("/com/innovatewithomer/juiceforu/order-view.fxml"))
            val root = loader.load<Parent>()
            val controller = loader.getController<OrderController>()

            val stage = Stage()
            val scene = Scene(root)

            // --- Detect screen info ---
            val screen = Screen.getPrimary()
            val visual = screen.visualBounds
            val scaleX = screen.outputScaleX
            val scaleY = screen.outputScaleY

            // --- Smart scaling logic ---
            val baseWidth = 900.0
            val baseHeight = 600.0

            val scaledWidth = when {
                visual.width < 1366 -> baseWidth * 0.85
                scaleX > 1.3 -> baseWidth * (1.0 / scaleX)
                else -> baseWidth
            }

            val scaledHeight = when {
                visual.height < 768 -> baseHeight * 0.85
                scaleY > 1.3 -> baseHeight * (1.0 / scaleY)
                else -> baseHeight
            }

            stage.scene = scene
            stage.title = "Edit Order"
            stage.isResizable = true
            stage.width = scaledWidth
            stage.height = scaledHeight

            // Center window
            stage.x = visual.minX + (visual.width - stage.width) / 2
            stage.y = visual.minY + (visual.height - stage.height) / 2

            // Pass reference to controller
            controller.editStage = stage
            controller.loadOrderForEdit(order)

            // ✅ Dispose controller coroutines when this window closes
            stage.setOnCloseRequest {
                controller.dispose()
            }

            stage.show()

            println("🪟 Opened Edit Order window at ${stage.width}×${stage.height} (scale $scaleX×$scaleY)")

        } catch (e: Exception) {
            e.printStackTrace()
            Logger.logError(e, e.message)
            showAlert("Error", "Failed to open order for editing: ${e.message}")
        }
    }


    suspend fun printReceipt(printerName: String, order: Order) = withContext(Dispatchers.IO) {
        val charset = Charset.forName("CP437")
        var escpos: EscPos? = null
        var outputStream: PrinterOutputStream? = null

        try {
            // ✅ Get printer list safely
//            val printServices: List<String> = PrinterOutputStream.getListPrintServicesNames() as List<String>
//            if (!printServices.contains(printerName)) {
//                throw IllegalArgumentException(
//                    "Printer '$printerName' not found. Available: ${printServices.joinToString()}"
//                )
//            }

            // ✅ Get printer service safely
            val printService: PrintService = PrinterOutputStream.getPrintServiceByName(printerName)
                ?: throw IllegalStateException("Printer service '$printerName' is null.")

            outputStream = PrinterOutputStream(printService)
            escpos = EscPos(outputStream)

            // --- HEADER ---
            outputStream.write(byteArrayOf(0x1B, 0x40)) // initialize
            outputStream.write(byteArrayOf(0x1B, 0x21, 0x30)) // double size
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x01)) // bold
            outputStream.write("Order No: ${order.orderNo}\n".toByteArray(charset))
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00)) // bold off
            outputStream.write(byteArrayOf(0x1B, 0x21, 0x00)) // normal
            outputStream.write("\n".toByteArray(charset))

            // --- LOGO ---
            val imageStream: InputStream? =
                javaClass.getResourceAsStream("/com/innovatewithomer/juiceforu/logo/logo.jpg")
            if (imageStream != null) {
                try {
                    val originalImage = ImageIO.read(imageStream)
                    val targetWidth = 300
                    val targetHeight = (originalImage.height * targetWidth) / originalImage.width

                    val resizedImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB).apply {
                        createGraphics().run {
                            drawImage(originalImage, 0, 0, targetWidth, targetHeight, null)
                            dispose()
                        }
                    }

                    val escPosImage = EscPosImage(
                        CoffeeImageImpl(adjustForThermalPrint(resizedImage)),
                        BitonalOrderedDither()
                    )

                    // ✅ Timeout protection (avoid hangs)
                    withTimeout(5000) {
                        escpos.write(BitImageWrapper().setJustification(EscPosConst.Justification.Center), escPosImage)
                    }
                    escpos.feed(2)
                } catch (imgEx: Exception) {
                    println("⚠️ Logo print failed: ${imgEx.message}")
                } finally {
                    imageStream.close()
                }
            } else {
                println("⚠️ Logo image not found in resources.")
            }

            // --- MAIN CONTENT ---
            val receiptBytes = buildReceiptContent(order.id)
            outputStream.write(receiptBytes)
            outputStream.flush()
            escpos.cut(EscPos.CutMode.FULL)

            println("✅ Receipt printed successfully for order ${order.orderNo}")
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Printing failed: ${e.message}")
            Logger.logError(e, e.message)
            Platform.runLater {
                showAlert("Printing Error", e.message ?: "Unknown error while printing.")
            }
        } finally {
            try {
                escpos?.close()
                outputStream?.close()
            } catch (ex: Exception) {
                Logger.logError(ex, ex.message)
                println("⚠️ Failed to close printer streams: ${ex.message}")
            }
        }
    }


    private fun adjustForThermalPrint(image: BufferedImage): BufferedImage {
        val width = image.width
        val height = image.height
        val adjusted = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = Color(image.getRGB(x, y))
                // brighten red and white areas more
                val r = (color.red * 1.4).coerceAtMost(255.0)
                val g = (color.green * 1.3).coerceAtMost(255.0)
                val b = (color.blue * 1.3).coerceAtMost(255.0)
                adjusted.setRGB(x, y, Color(r.toInt(), g.toInt(), b.toInt()).rgb)
            }
        }

        return adjusted
    }

    override fun dispose() {
        ioScope.cancel("Controller disposed")
    }

    private fun convertToHighContrast(image: BufferedImage): BufferedImage {
        val converter = ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
        return converter.filter(image, null)
    }
}
