package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.models.InventoryItem
import com.innovatewithomer.juiceforu.BrandAssets
import com.innovatewithomer.juiceforu.StockQuantity
import com.innovatewithomer.juiceforu.models.MenuItem
import com.innovatewithomer.juiceforu.models.RecipeItem
import com.innovatewithomer.juiceforu.models.RecipeUsage
import com.innovatewithomer.juiceforu.repo.RecipeRepository
import com.innovatewithomer.juiceforu.repo.InventoryRepository
import com.innovatewithomer.juiceforu.repo.MenuItemRepository
import com.innovatewithomer.juiceforu.utils.DisposableController
import com.innovatewithomer.juiceforu.utils.Logger
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RecipeController : DisposableController {

    @FXML private lateinit var menuItemCombo: ComboBox<MenuItem>
    @FXML private lateinit var ingredientCombo: ComboBox<InventoryItem>
    @FXML private lateinit var quantityField: TextField
    @FXML private lateinit var takeawayDeliveryOnlyCheck: CheckBox
    @FXML private lateinit var recipeTable: TableView<RecipeRow>
    @FXML private lateinit var colIngredient: TableColumn<RecipeRow, String>
    @FXML private lateinit var colQuantity: TableColumn<RecipeRow, String>
    @FXML private lateinit var colUnit: TableColumn<RecipeRow, String>
    @FXML private lateinit var colUsage: TableColumn<RecipeRow, String>

    private val recipeRepo = RecipeRepository()
    private val menuRepo = MenuItemRepository
    private val inventoryRepo = InventoryRepository()

    private val recipeRows = FXCollections.observableArrayList<RecipeRow>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recipeLoadJob: Job? = null
    private var recipeLoadVersion = 0
    private var saving = false

    @FXML
    fun initialize() {
        menuItemCombo.items = FXCollections.observableArrayList()
        ingredientCombo.items = FXCollections.observableArrayList()

        // Recipes belong to a specific menu variant, not just a product name.
        menuItemCombo.setCellFactory {
            object : ListCell<MenuItem>() {
                override fun updateItem(item: MenuItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) null else recipeMenuItemLabel(item)
                    tooltip = if (empty || item == null) null else Tooltip(text)
                }
            }
        }
        menuItemCombo.buttonCell = object : ListCell<MenuItem>() {
            override fun updateItem(item: MenuItem?, empty: Boolean) {
                super.updateItem(item, empty)
                text = if (empty || item == null) null else recipeMenuItemLabel(item)
            }
        }
        menuItemCombo.valueProperty().addListener { _, _, selected ->
            menuItemCombo.tooltip = selected?.let { Tooltip(recipeMenuItemLabel(it)) }
        }

        // Show only the name for Ingredients
        ingredientCombo.setCellFactory {
            object : ListCell<InventoryItem>() {
                override fun updateItem(item: InventoryItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.name
                }
            }
        }
        ingredientCombo.buttonCell = object : ListCell<InventoryItem>() {
            override fun updateItem(item: InventoryItem?, empty: Boolean) {
                super.updateItem(item, empty)
                text = if (empty || item == null) "" else item.name
            }
        }

        // Setup table columns
        colIngredient.setCellValueFactory { SimpleStringProperty(it.value.ingredientName) }
        colQuantity.setCellValueFactory { SimpleStringProperty(StockQuantity.format(it.value.quantityNeeded)) }
        colUnit.setCellValueFactory { SimpleStringProperty(it.value.unit) }
        colUsage.setCellValueFactory { SimpleStringProperty(it.value.usage.label) }

        recipeTable.items = recipeRows

        // Load recipes when a menu item is selected
        menuItemCombo.setOnAction {
            menuItemCombo.value?.id?.let { loadRecipesForMenuItem(it) }
        }

        ioScope.launch {
            try {
                val menuItems = menuRepo.getAllMenuItems()
                val ingredients = inventoryRepo.getAllItems()
                Platform.runLater {
                    menuItemCombo.items.setAll(menuItems)
                    ingredientCombo.items.setAll(ingredients)
                    if (menuItems.isNotEmpty()) menuItemCombo.selectionModel.selectFirst()
                }
            } catch (e: Exception) {
                Logger.logError(e, "Could not load recipe choices")
                Platform.runLater { showAlert("Recipes unavailable", "Could not load recipe choices: ${e.message}") }
            }
        }

        recipeTable.selectionModel.selectedItemProperty().addListener { _, _, newSelection ->
            if (newSelection != null) {
                ingredientCombo.selectionModel.select(
                    ingredientCombo.items.find { it.id == newSelection.ingredientId }
                )
                quantityField.text = StockQuantity.format(newSelection.quantityNeeded)
                takeawayDeliveryOnlyCheck.isSelected = newSelection.usage == RecipeUsage.TAKEAWAY_DELIVERY
            }
        }
    }


    private fun loadRecipesForMenuItem(menuItemId: Int) {
        val version = ++recipeLoadVersion
        recipeRows.clear()
        quantityField.clear()
        ingredientCombo.selectionModel.clearSelection()
        takeawayDeliveryOnlyCheck.isSelected = false
        recipeLoadJob?.cancel()
        val ingredientsById = ingredientCombo.items.associateBy { it.id }
        recipeLoadJob = ioScope.launch {
            try {
                val rows = recipeRepo.getRecipesForMenuItem(menuItemId).mapNotNull { recipe ->
                    val ingredient = ingredientsById[recipe.ingredientId] ?: return@mapNotNull null
                    RecipeRow(recipe.id, ingredient.id, ingredient.name, ingredient.unit, recipe.quantityNeeded, recipe.usage)
                }
                Platform.runLater {
                    if (version == recipeLoadVersion && menuItemCombo.value?.id == menuItemId) recipeRows.setAll(rows)
                }
            } catch (e: Exception) {
                Logger.logError(e, "Could not load recipes")
            }
        }
    }

    override fun dispose() = ioScope.cancel()


    @FXML
    fun handleAddRecipe() {
        val menuItem = menuItemCombo.value
        val ingredient = ingredientCombo.value
        val qty = StockQuantity.parse(quantityField.text)
        if (menuItem == null || ingredient == null || qty == null || qty <= 0) {
            showAlert("Incomplete recipe", "Choose a menu item and ingredient, then enter a quantity greater than zero with at most 2 decimal places.")
            return
        }

        // ✅ Prevent duplicate ingredient for same menu item
        if (recipeRows.any { it.ingredientId == ingredient.id }) {
            showAlert("Duplicate Ingredient", "This ingredient is already added for the selected menu item.")
            return
        }

        val recipe = RecipeItem(menuItemId = menuItem.id ?: 0, ingredientId = ingredient.id, quantityNeeded = qty,
            usage = if (takeawayDeliveryOnlyCheck.isSelected) RecipeUsage.TAKEAWAY_DELIVERY else RecipeUsage.ALL)
        saveRecipeChange(menuItem.id ?: 0) { recipeRepo.addRecipe(recipe) }
    }

    @FXML
    fun handleEditRecipe() {
        val selected = recipeTable.selectionModel.selectedItem ?: run {
            showAlert("No recipe selected", "Select a recipe row to update.")
            return
        }
        val ingredient = ingredientCombo.value
        val qty = StockQuantity.parse(quantityField.text)
        val menuItem = menuItemCombo.value
        if (ingredient == null || menuItem == null || qty == null || qty <= 0) {
            showAlert("Invalid recipe", "Choose an ingredient and enter a quantity greater than zero with at most 2 decimal places.")
            return
        }

        // Prevent duplicate ingredient for same menu item (except same row being edited)
        if (recipeRows.any { it.ingredientId == ingredient.id && it.id != selected.id }) {
            showAlert("Duplicate Ingredient", "This ingredient is already used for the selected menu item.")
            return
        }

        val recipe = RecipeItem(id = selected.id, menuItemId = menuItem.id ?: 0, ingredientId = ingredient.id,
            quantityNeeded = qty, usage = if (takeawayDeliveryOnlyCheck.isSelected) RecipeUsage.TAKEAWAY_DELIVERY else RecipeUsage.ALL)
        saveRecipeChange(menuItem.id ?: 0) { recipeRepo.updateRecipe(recipe) }
    }


    @FXML
    fun handleDeleteRecipe() {
        val selected = recipeTable.selectionModel.selectedItem ?: run {
            showAlert("No recipe selected", "Select a recipe row to delete.")
            return
        }

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Delete Recipe"
        alert.headerText = "Are you sure you want to delete this recipe?"
        alert.contentText = "Ingredient: ${selected.ingredientName}, Quantity: ${StockQuantity.format(selected.quantityNeeded)} ${selected.unit}"

        // ✅ Window icon (top-left corner of dialog window)
        val stage = alert.dialogPane.scene.window as javafx.stage.Stage
        stage.icons.add(BrandAssets.logo)

        // ✅ Dialog graphic (small icon next to text)
        val img = Image(javaClass.getResourceAsStream("/com/innovatewithomer/juiceforu/icons/delete.png"))
        val imgView = ImageView(img)
        imgView.fitWidth = 32.0   // keep it small
        imgView.fitHeight = 32.0
        imgView.isPreserveRatio = true
        alert.graphic = imgView

        // ✅ Apply custom style
        alert.dialogPane.stylesheets.add(
            javaClass.getResource("/com/innovatewithomer/juiceforu/styles/style.css").toExternalForm()
        )

        val result = alert.showAndWait()
        if (result.isPresent && result.get() == ButtonType.OK) {
            val menuItemId = menuItemCombo.value?.id ?: return
            saveRecipeChange(menuItemId) { recipeRepo.deleteRecipe(selected.id) }
        }
    }

    private fun saveRecipeChange(menuItemId: Int, change: () -> Unit) {
        if (saving) return
        saving = true
        recipeTable.isDisable = true
        menuItemCombo.isDisable = true
        val version = ++recipeLoadVersion
        recipeLoadJob?.cancel()
        val ingredientsById = ingredientCombo.items.associateBy { it.id }
        ioScope.launch {
            try {
                change()
                val rows = recipeRepo.getRecipesForMenuItem(menuItemId).mapNotNull { recipe ->
                    val ingredient = ingredientsById[recipe.ingredientId] ?: return@mapNotNull null
                    RecipeRow(recipe.id, ingredient.id, ingredient.name, ingredient.unit, recipe.quantityNeeded, recipe.usage)
                }
                Platform.runLater {
                    if (version == recipeLoadVersion && menuItemCombo.value?.id == menuItemId) recipeRows.setAll(rows)
                    quantityField.clear()
                    ingredientCombo.selectionModel.clearSelection()
                    takeawayDeliveryOnlyCheck.isSelected = false
                }
            } catch (e: Exception) {
                Logger.logError(e, "Could not save recipe")
                Platform.runLater { showAlert("Recipe change failed", e.message ?: "The recipe change could not be saved.") }
            } finally {
                Platform.runLater {
                    saving = false
                    recipeTable.isDisable = false
                    menuItemCombo.isDisable = false
                }
            }
        }
    }



    private fun showAlert(title: String, message: String) {
        val alert = Alert(Alert.AlertType.WARNING)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    data class RecipeRow(
        val id: Int,
        val ingredientId: Int,
        val ingredientName: String,
        val unit: String,
        val quantityNeeded: Double,
        val usage: RecipeUsage
    )
}

internal fun recipeMenuItemLabel(item: MenuItem): String = buildString {
    append(item.name)
    if (item.size.isNotBlank()) append(" (${item.size})")
    if (item.category.isNotBlank()) append(" · ${item.category}")
}
