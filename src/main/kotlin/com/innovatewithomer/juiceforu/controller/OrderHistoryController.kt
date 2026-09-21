package com.innovatewithomer.juiceforu.controller

import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.image.BitImageWrapper
import com.github.anastaciocintra.escpos.image.BitonalOrderedDither
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl
import com.github.anastaciocintra.escpos.image.EscPosImage
import com.github.anastaciocintra.output.PrinterOutputStream
import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.AppSettings
import com.innovatewithomer.juiceforu.PrinterService
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
import kotlinx.coroutines.CancellationException
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
    private var removeOrderListener: (() -> Unit)? = null

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
                private val label = Label().apply {
                    isWrapText = true
                    maxWidthProperty().bind(itemsColumn.widthProperty().subtract(18.0))
                }
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                    } else {
                        label.text = item
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
                                    val printerName = AppSettings.printerName ?: PrinterService.defaultPrinterName()
                                    if (printerName.isNullOrBlank()) {
                                        Platform.runLater {
                                            showAlert("Printer not configured", "Select a receipt printer in Settings before printing.")
                                        }
                                        return@launch
                                    }
                                    printReceipt(printerName, order)
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
                private var updatingCell = false
                private val comboBox = ComboBox<String>().apply {
                    items = FXCollections.observableArrayList("PENDING", "COMPLETED", "CANCELLED")
                    prefWidth = 125.0
                    style = "-fx-font-size: 13px;"
                    setOnAction {
                        if (updatingCell) return@setOnAction
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
                        updatingCell = true
                        comboBox.value = item
                        updatingCell = false
                        graphic = comboBox
                    }
                }
            }
        }

        // Event listeners to reload
        removeOrderListener = OrderEvents.addListener {
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
                val todayOrders = repo.getOrdersByDate("today")
                Platform.runLater {
                    ordersTable.items = FXCollections.observableArrayList(todayOrders)
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
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
                val summary = repo.getSalesSummary()
                Platform.runLater {
                    todaySalesLabel.text = "Today: Rs. ${summary["today"]?.toInt() ?: 0}"
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
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

    private fun openOrderForEdit(order: Order) {
        try {
            val loader = FXMLLoader(javaClass.getResource("/com/innovatewithomer/juiceforu/order-view.fxml"))
            val root = loader.load<Parent>()
            val controller = loader.getController<OrderController>()

            val stage = Stage()
            val scene = Scene(root)

            val screen = Screen.getPrimary()
            val visual = screen.visualBounds
            val scaleX = screen.outputScaleX
            val scaleY = screen.outputScaleY

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

            stage.x = visual.minX + (visual.width - stage.width) / 2
            stage.y = visual.minY + (visual.height - stage.height) / 2

            controller.editStage = stage
            controller.loadOrderForEdit(order)

            stage.setOnCloseRequest {
                controller.dispose()
            }

            stage.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.logError(e, e.message)
            showAlert("Error", "Failed to open order for editing: ${e.message}")
        }
    }

    private val receiptPrinter = com.innovatewithomer.juiceforu.utils.ReceiptPrinter()

    suspend fun printReceipt(printerName: String, order: Order) = withContext(Dispatchers.IO) {
        try {
            receiptPrinter.printReceipt(printerName, order, isOldOrder = order.isEdited)
        } catch (e: Exception) {
            Logger.logError(e, "OrderHistory printing error")
            throw e
        }
    }

    override fun dispose() {
        removeOrderListener?.invoke()
        removeOrderListener = null
        ioScope.cancel("Controller disposed")
    }
}
