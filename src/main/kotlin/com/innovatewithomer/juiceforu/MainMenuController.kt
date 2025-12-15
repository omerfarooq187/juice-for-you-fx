package com.innovatewithomer.juiceforu

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import kotlin.system.exitProcess

class MainMenuController {

    @FXML
    private lateinit var contentArea: BorderPane

    @FXML
    private lateinit var headerTitle: Label

    @FXML
    fun initialize() {
        // load dashboard by default
        loadView("/com/innovatewithomer/juiceforu/Dashboard.fxml", "Dashboard")
    }

    private fun loadView(resourcePath: String, title: String) {
        val url = javaClass.getResource(resourcePath)
        if (url == null) {
            println("❌ FXML not found: $resourcePath")
            return
        }
        val loader = FXMLLoader(url)
        val view: Node = loader.load()
        contentArea.center = view
        headerTitle.text = title
    }


    @FXML
    fun onDashboardClick() = loadView("/com/innovatewithomer/juiceforu/Dashboard.fxml", "Dashboard")

    @FXML
    fun onNewOrderClick() = loadView("/com/innovatewithomer/juiceforu/order-view.fxml", "New Order")

    @FXML
    fun onOrderHistoryClick() = loadView("/com/innovatewithomer/juiceforu/OrderHistory.fxml", "Order History")

    @FXML
    fun onMenuClick() = loadView("/com/innovatewithomer/juiceforu/Menu.fxml", "Menu Items")

    @FXML
    fun onInventoryClick() = loadView("/com/innovatewithomer/juiceforu/inventory.fxml", "Inventory")

    @FXML
    fun onRecipeClick() = loadView("/com/innovatewithomer/juiceforu/Recipe.fxml", "Recipe")

//    @FXML
//    fun onSettingsClick() = loadView("/com/innovatewithomer/juiceforu/Settings.fxml", "Settings")

    @FXML
    fun onExitClick(): Nothing = exitProcess(0)
}
