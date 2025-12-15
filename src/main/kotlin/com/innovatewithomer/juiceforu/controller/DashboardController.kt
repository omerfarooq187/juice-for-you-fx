package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.repo.DashboardRepository
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.chart.PieChart
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class RecentOrder(val id: Int, val total: Int, val createdAt: String)

class DashboardController {

    @FXML private lateinit var salesLabel: Label
    @FXML private lateinit var ordersLabel: Label
    @FXML private lateinit var topItemsChart: PieChart
    @FXML private lateinit var recentOrdersTable: TableView<RecentOrder>
    @FXML private lateinit var orderIdCol: TableColumn<RecentOrder, Int>
    @FXML private lateinit var totalCol: TableColumn<RecentOrder, Int>
    @FXML private lateinit var createdAtCol: TableColumn<RecentOrder, String>

    @FXML private lateinit var avgOrderLabel: Label


    @FXML
    fun initialize() {
        loadDashboard()
    }

    private fun loadDashboard() {
        val todaySales = DashboardRepository.getTodaySales()
        salesLabel.text = "Rs. $todaySales"

        // 🔹 Today’s orders
        ordersLabel.text = DashboardRepository.getTodayOrders().toString()

        val avgOrder = if (DashboardRepository.getTodayOrders() > 0)
            todaySales / DashboardRepository.getTodayOrders()
        else 0
        avgOrderLabel.text = "Rs. $avgOrder"


        // 🔹 Top Selling Items → PieChart
        val topItems = DashboardRepository.getTopSellingItems()
        topItemsChart.data = FXCollections.observableArrayList(
            topItems.map { PieChart.Data(it.first, it.second.toDouble()) }
        )

        // 🔹 Table Columns
        orderIdCol.cellValueFactory = PropertyValueFactory("id")
        totalCol.cellValueFactory = PropertyValueFactory("total")

        createdAtCol.setCellValueFactory { cellData ->
            val millis = cellData.value.createdAt.toLong()
            val formatted = try {
                val dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(millis),
                    ZoneId.systemDefault()
                )
                dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a"))
            } catch (e: Exception) {
                millis.toString() // fallback if parsing fails
            }
            SimpleStringProperty(formatted)
        }

        // 🔹 Recent Orders
        val recentOrders = DashboardRepository.getRecentOrders().map {
            RecentOrder(it.first, it.second, it.third)
        }
        recentOrdersTable.items = FXCollections.observableArrayList(recentOrders)
    }

    // 🔄 Refresh button handler
    @FXML
    private fun onRefreshClick() {
        loadDashboard()
    }
}
