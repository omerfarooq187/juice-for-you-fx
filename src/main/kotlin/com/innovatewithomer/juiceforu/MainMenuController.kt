package com.innovatewithomer.juiceforu

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.application.Platform
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.util.Duration
import com.innovatewithomer.juiceforu.utils.DisposableController
import java.time.format.DateTimeFormatter

class MainMenuController {

    @FXML
    private lateinit var contentArea: BorderPane

    @FXML
    private lateinit var headerTitle: Label
    @FXML private lateinit var dateLabel: Label
    @FXML private lateinit var brandLogo: ImageView
    @FXML private lateinit var brandNameLabel: Label
    @FXML private lateinit var topBrandNameLabel: Label
    @FXML private lateinit var dashboardButton: Button
    @FXML private lateinit var newOrderButton: Button
    @FXML private lateinit var historyButton: Button
    @FXML private lateinit var menuButton: Button
    @FXML private lateinit var inventoryButton: Button
    @FXML private lateinit var recipeButton: Button
    @FXML private lateinit var settingsButton: Button

    private var activeController: Any? = null
    private var loadFailures = 0
    private val dateRefresh = Timeline(KeyFrame(Duration.seconds(30.0), javafx.event.EventHandler { updateBusinessDateLabel() }))
        .apply { cycleCount = Timeline.INDEFINITE }

    @FXML
    fun initialize() {
        brandLogo.image = BrandAssets.logo
        brandNameLabel.text = AppBrand.current.businessName
        topBrandNameLabel.text = AppBrand.current.businessName.uppercase()
        updateBusinessDateLabel()
        dateRefresh.play()
        loadView("/com/innovatewithomer/juiceforu/Dashboard.fxml", "Dashboard", dashboardButton)
    }

    private fun loadView(resourcePath: String, title: String, selectedButton: Button) {
        val url = javaClass.getResource(resourcePath)
        if (url == null) {
            loadFailures++
            showLoadError("The screen resource could not be found: $resourcePath")
            return
        }
        try {
            val loader = FXMLLoader(url)
            val view: Node = loader.load()
            (activeController as? DisposableController)?.disposeSafely()
            activeController = loader.getController<Any>()
            contentArea.center = view
            headerTitle.text = title
            updateBusinessDateLabel()
            listOf(dashboardButton, newOrderButton, historyButton, menuButton, inventoryButton, recipeButton, settingsButton)
                .forEach { it.styleClass.remove("active") }
            selectedButton.styleClass.add("active")
        } catch (e: Exception) {
            loadFailures++
            e.printStackTrace()
            showLoadError("$title could not be opened. ${e.message ?: "Please check the application log."}")
        }
    }

    private fun updateBusinessDateLabel() {
        dateLabel.text = BusinessDay.currentDate().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
    }


    @FXML
    fun onDashboardClick() = loadView("/com/innovatewithomer/juiceforu/Dashboard.fxml", "Dashboard", dashboardButton)

    @FXML
    fun onNewOrderClick() = loadView("/com/innovatewithomer/juiceforu/order-view.fxml", "New Order", newOrderButton)

    @FXML
    fun onOrderHistoryClick() = loadView("/com/innovatewithomer/juiceforu/OrderHistory.fxml", "Order History", historyButton)

    @FXML
    fun onMenuClick() = loadView("/com/innovatewithomer/juiceforu/Menu.fxml", "Menu Items", menuButton)

    @FXML
    fun onInventoryClick() = loadView("/com/innovatewithomer/juiceforu/inventory.fxml", "Inventory", inventoryButton)

    @FXML
    fun onRecipeClick() = loadView("/com/innovatewithomer/juiceforu/Recipe.fxml", "Recipes", recipeButton)

    @FXML
    fun onSettingsClick() = loadView("/com/innovatewithomer/juiceforu/Settings.fxml", "Settings", settingsButton)

    @FXML
    fun onExitClick() {
        dateRefresh.stop()
        (activeController as? DisposableController)?.disposeSafely()
        Platform.exit()
    }

    internal fun smokeTestViews() {
        loadFailures = 0
        onNewOrderClick()
        onOrderHistoryClick()
        onMenuClick()
        onInventoryClick()
        onRecipeClick()
        onSettingsClick()
        onDashboardClick()
        check(contentArea.center != null) { "No application screen was loaded" }
        check(loadFailures == 0) { "$loadFailures application screen(s) failed to load" }
    }

    private fun showLoadError(message: String) {
        Alert(Alert.AlertType.ERROR).apply {
            title = "Unable to open screen"
            headerText = null
            contentText = message
        }.showAndWait()
    }
}
