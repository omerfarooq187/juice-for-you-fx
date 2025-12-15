package com.innovatewithomer.juiceforu.models

data class InventoryItem(
    val id: Int = 0,
    var name: String,
    var unit: String,
    var quantity: Double,
    val reorderLevel: Double
)

data class RecipeItem(
    val id: Int = 0,
    val menuItemId: Int,
    val ingredientId: Int,
    val quantityNeeded: Double
)
