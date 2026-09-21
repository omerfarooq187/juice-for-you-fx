package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.StockQuantity
import com.innovatewithomer.juiceforu.models.RecipeItem
import com.innovatewithomer.juiceforu.models.RecipeUsage
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet

class RecipeRepository {

    fun addRecipe(recipe: RecipeItem, conn: Connection? = null) {
        val sql = "INSERT INTO recipes (menu_item_id, ingredient_id, quantity_needed, fulfillment_scope) VALUES (?, ?, ?, ?)"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, recipe.menuItemId)
                stmt.setInt(2, recipe.ingredientId)
                stmt.setDouble(3, StockQuantity.round(recipe.quantityNeeded))
                stmt.setString(4, recipe.usage.name)
                stmt.executeUpdate()
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    fun updateRecipe(recipe: RecipeItem, conn: Connection? = null) {
        val sql = "UPDATE recipes SET ingredient_id=?, quantity_needed=?, fulfillment_scope=? WHERE id=?"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, recipe.ingredientId)
                stmt.setDouble(2, StockQuantity.round(recipe.quantityNeeded))
                stmt.setString(3, recipe.usage.name)
                stmt.setInt(4, recipe.id)
                stmt.executeUpdate()
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    fun deleteRecipe(id: Int, conn: Connection? = null) {
        val sql = "DELETE FROM recipes WHERE id=?"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, id)
                stmt.executeUpdate()
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    fun getRecipesForMenuItem(menuItemId: Int, conn: Connection? = null): List<RecipeItem> {
        val list = mutableListOf<RecipeItem>()
        val sql = "SELECT * FROM recipes WHERE menu_item_id=?"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, menuItemId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(resultToRecipe(rs))
                    }
                }
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
        return list
    }

    /** Aggregates one order's ingredient use, applying per-recipe fulfillment rules. */
    fun calculateUsage(
        items: List<Pair<Int, Int>>,
        orderType: String,
        includeConditionalRecipes: Boolean = true
    ): List<Pair<Int, Double>> {
        val quantitiesByMenu = items.filter { it.first > 0 && it.second > 0 }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, counts) -> counts.sum() }
        if (quantitiesByMenu.isEmpty()) return emptyList()

        val ids = quantitiesByMenu.keys.toList()
        val placeholders = List(ids.size) { "?" }.joinToString(",")
        val totals = mutableMapOf<Int, BigDecimal>()
        Database.getConnection().use { conn ->
            conn.prepareStatement("SELECT * FROM recipes WHERE menu_item_id IN ($placeholders)").use { stmt ->
                ids.forEachIndexed { index, id -> stmt.setInt(index + 1, id) }
                stmt.executeQuery().use { rows ->
                    while (rows.next()) {
                        val recipe = resultToRecipe(rows)
                        if (!recipe.usage.appliesTo(orderType) ||
                            (!includeConditionalRecipes && recipe.usage != RecipeUsage.ALL)) continue
                        val amount = BigDecimal.valueOf(recipe.quantityNeeded)
                            .multiply(BigDecimal.valueOf(quantitiesByMenu.getValue(recipe.menuItemId).toLong()))
                        totals.merge(recipe.ingredientId, amount, BigDecimal::add)
                    }
                }
            }
        }
        return totals.toSortedMap().map { (ingredientId, quantity) ->
            ingredientId to StockQuantity.round(quantity.toDouble())
        }
    }

    private fun resultToRecipe(rs: ResultSet): RecipeItem {
        return RecipeItem(
            id = rs.getInt("id"),
            menuItemId = rs.getInt("menu_item_id"),
            ingredientId = rs.getInt("ingredient_id"),
            quantityNeeded = rs.getDouble("quantity_needed"),
            usage = RecipeUsage.fromStorage(rs.getString("fulfillment_scope"))
        )
    }
}
