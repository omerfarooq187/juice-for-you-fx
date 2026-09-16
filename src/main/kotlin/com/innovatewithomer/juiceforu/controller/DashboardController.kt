package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.repo.DashboardRepository
import com.innovatewithomer.juiceforu.utils.DisposableController
import com.innovatewithomer.juiceforu.utils.Logger
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.chart.PieChart
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecentOrder(val id: Int, val total: Int, val createdAt: String)

class DashboardController : DisposableController {

    @FXML private lateinit var salesLabel: Label
    @FXML private lateinit var ordersLabel: Label
    @FXML private lateinit var topItemsChart: PieChart
    @FXML private lateinit var recentOrdersTable: TableView<RecentOrder>
    @FXML private lateinit var orderIdCol: TableColumn<RecentOrder, Int>
    @FXML private lateinit var totalCol: TableColumn<RecentOrder, Int>
    @FXML private lateinit var createdAtCol: TableColumn<RecentOrder, String>
    @FXML private lateinit var avgOrderLabel: Label

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @FXML
    fun initialize() {
        orderIdCol.cellValueFactory = PropertyValueFactory("id")
        totalCol.cellValueFactory = PropertyValueFactory("total")

        createdAtCol.setCellValueFactory { cellData ->
            val millis = cellData.value.createdAt.toLongOrNull()
            val formatted = if (millis != null && millis > 0) {
                try {
                    val dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(millis),
                        ZoneId.systemDefault()
                    )
                    dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a"))
                } catch (e: Exception) {
                    millis.toString()
                }
            } else {
                cellData.value.createdAt
            }
            SimpleStringProperty(formatted)
        }

        loadDashboard()
    }

    private fun loadDashboard() {
        ioScope.launch {
            try {
                val todaySales = DashboardRepository.getTodaySales()
                val todayOrders = DashboardRepository.getTodayOrders()
                val avgOrder = if (todayOrders > 0) todaySales / todayOrders else 0
                val topItems = DashboardRepository.getTopSellingItems()
                val recentOrders = DashboardRepository.getRecentOrders().map {
                    RecentOrder(it.first, it.second, it.third)
                }

                Platform.runLater {
                    salesLabel.text = "Rs. $todaySales"
                    ordersLabel.text = todayOrders.toString()
                    avgOrderLabel.text = "Rs. $avgOrder"

                    topItemsChart.data = FXCollections.observableArrayList(
                        topItems.map { PieChart.Data(it.first, it.second.toDouble()) }
                    )

                    recentOrdersTable.items = FXCollections.observableArrayList(recentOrders)
                }
            } catch (e: Exception) {
                Logger.logError(e, "Error loading dashboard")
            }
        }
    }

    @FXML
    private fun onRefreshClick() {
        loadDashboard()
    }

    override fun dispose() {
        ioScope.cancel()
    }
}

