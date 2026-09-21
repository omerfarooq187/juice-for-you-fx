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
    val quantityNeeded: Double,
    val usage: RecipeUsage = RecipeUsage.ALL
)

enum class RecipeUsage(val label: String) {
    ALL("All order types"),
    TAKEAWAY_DELIVERY("Takeaway / Delivery only");

    fun appliesTo(orderType: String): Boolean = when (this) {
        ALL -> true
        TAKEAWAY_DELIVERY -> orderType.equals("Takeaway", ignoreCase = true) ||
            orderType.equals("Delivery", ignoreCase = true)
    }

    companion object {
        fun fromStorage(value: String?): RecipeUsage = entries.firstOrNull { it.name == value } ?: ALL
    }
}
