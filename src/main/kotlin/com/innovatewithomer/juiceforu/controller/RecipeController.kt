package com.innovatewithomer.juiceforu.controller

import com.innovatewithomer.juiceforu.models.InventoryItem
import com.innovatewithomer.juiceforu.models.MenuItem
import com.innovatewithomer.juiceforu.models.RecipeItem
import com.innovatewithomer.juiceforu.repo.RecipeRepository
import com.innovatewithomer.juiceforu.repo.InventoryRepository
import com.innovatewithomer.juiceforu.repo.MenuItemRepository
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView

class RecipeController {

    @FXML private lateinit var menuItemCombo: ComboBox<MenuItem>
    @FXML private lateinit var ingredientCombo: ComboBox<InventoryItem>
    @FXML private lateinit var quantityField: TextField
    @FXML private lateinit var recipeTable: TableView<RecipeRow>
    @FXML private lateinit var colIngredient: TableColumn<RecipeRow, String>
    @FXML private lateinit var colQuantity: TableColumn<RecipeRow, Number>
    @FXML private lateinit var colUnit: TableColumn<RecipeRow, String>

    private val recipeRepo = RecipeRepository()
    private val menuRepo = MenuItemRepository
    private val inventoryRepo = InventoryRepository()

    private val recipeRows = FXCollections.observableArrayList<RecipeRow>()

    @FXML
    fun initialize() {
        // Load data into combos
        menuItemCombo.items = FXCollections.observableArrayList(menuRepo.getAllMenuItems())
        ingredientCombo.items = FXCollections.observableArrayList(inventoryRepo.getAllItems())

        // Show only the name for Menu Items
        menuItemCombo.setCellFactory {
            object : ListCell<MenuItem>() {
                override fun updateItem(item: MenuItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.name
                }
            }
        }
        menuItemCombo.buttonCell = object : ListCell<MenuItem>() {
            override fun updateItem(item: MenuItem?, empty: Boolean) {
                super.updateItem(item, empty)
                text = if (empty || item == null) "" else item.name
            }
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
        colQuantity.setCellValueFactory { ReadOnlyObjectWrapper(it.value.quantityNeeded) }
        colUnit.setCellValueFactory { SimpleStringProperty(it.value.unit) }

        recipeTable.items = recipeRows

        // Load recipes when a menu item is selected
        menuItemCombo.setOnAction {
            menuItemCombo.value?.id?.let { loadRecipesForMenuItem(it) }
        }

        // Auto-select the first menu item
        if (menuItemCombo.items.isNotEmpty()) {
            menuItemCombo.selectionModel.selectFirst()
            menuItemCombo.value?.id?.let { loadRecipesForMenuItem(it) }
        }

        recipeTable.selectionModel.selectedItemProperty().addListener { _, _, newSelection ->
            if (newSelection != null) {
                ingredientCombo.selectionModel.select(
                    ingredientCombo.items.find { it.id == newSelection.ingredientId }
                )
                quantityField.text = newSelection.quantityNeeded.toString()
            }
        }
    }


    private fun loadRecipesForMenuItem(menuItemId: Int) {
        recipeRows.clear()
        val recipes = recipeRepo.getRecipesForMenuItem(menuItemId)
        val allIngredients = inventoryRepo.getAllItems()
        recipes.forEach { recipe ->
            val ingredient = allIngredients.find { it.id == recipe.ingredientId }
            if (ingredient != null) {
                recipeRows.add(
                    RecipeRow(
                        id = recipe.id,   // ✅ now real recipe id
                        ingredientId = ingredient.id,
                        ingredientName = ingredient.name,
                        unit = ingredient.unit,
                        quantityNeeded = recipe.quantityNeeded
                    )
                )
            }
        }
    }


    @FXML
    fun handleAddRecipe() {
        val menuItem = menuItemCombo.value ?: return
        val ingredient = ingredientCombo.value ?: return
        val qty = quantityField.text.toDoubleOrNull() ?: return

        // ✅ Prevent duplicate ingredient for same menu item
        if (recipeRows.any { it.ingredientId == ingredient.id }) {
            showAlert("Duplicate Ingredient", "This ingredient is already added for the selected menu item.")
            return
        }

        recipeRepo.addRecipe(
            RecipeItem(
                menuItemId = menuItem.id ?: 0,
                ingredientId = ingredient.id,
                quantityNeeded = qty
            )
        )

        loadRecipesForMenuItem(menuItem.id ?: 0)
        quantityField.clear()
    }

    @FXML
    fun handleEditRecipe() {
        val selected = recipeTable.selectionModel.selectedItem ?: return
        val ingredient = ingredientCombo.value ?: return
        val qty = quantityField.text.toDoubleOrNull() ?: return
        val menuItem = menuItemCombo.value ?: return

        // Prevent duplicate ingredient for same menu item (except same row being edited)
        if (recipeRows.any { it.ingredientId == ingredient.id && it.id != selected.id }) {
            showAlert("Duplicate Ingredient", "This ingredient is already used for the selected menu item.")
            return
        }

        recipeRepo.updateRecipe(
            RecipeItem(
                id = selected.id,
                menuItemId = menuItem.id ?: 0,
                ingredientId = ingredient.id,
                quantityNeeded = qty
            )
        )

        loadRecipesForMenuItem(menuItem.id ?: 0)
        quantityField.clear()
        ingredientCombo.selectionModel.clearSelection()
    }


    @FXML
    fun handleDeleteRecipe() {
        val selected = recipeTable.selectionModel.selectedItem ?: return

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Delete Recipe"
        alert.headerText = "Are you sure you want to delete this recipe?"
        alert.contentText = "Ingredient: ${selected.ingredientName}, Quantity: ${selected.quantityNeeded} ${selected.unit}"

        // ✅ Window icon (top-left corner of dialog window)
        val stage = alert.dialogPane.scene.window as javafx.stage.Stage
        stage.icons.add(Image(javaClass.getResourceAsStream("/com/innovatewithomer/juiceforu/logo/logo.jpg")))

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
            recipeRepo.deleteRecipe(selected.id)
            menuItemCombo.value?.id?.let { loadRecipesForMenuItem(it) }

            // Clear fields after delete
            quantityField.clear()
            ingredientCombo.selectionModel.clearSelection()
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
        val quantityNeeded: Double
    )
}
