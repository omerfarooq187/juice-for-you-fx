package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.RecipeItem
import java.sql.Connection
import java.sql.ResultSet

class RecipeRepository {

    fun addRecipe(recipe: RecipeItem, conn: Connection? = null) {
        val sql = "INSERT INTO recipes (menu_item_id, ingredient_id, quantity_needed) VALUES (?, ?, ?)"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, recipe.menuItemId)
                stmt.setInt(2, recipe.ingredientId)
                stmt.setDouble(3, recipe.quantityNeeded)
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
        val sql = "UPDATE recipes SET ingredient_id=?, quantity_needed=? WHERE id=?"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, recipe.ingredientId)
                stmt.setDouble(2, recipe.quantityNeeded)
                stmt.setInt(3, recipe.id)
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

    private fun resultToRecipe(rs: ResultSet): RecipeItem {
        return RecipeItem(
            id = rs.getInt("id"),
            menuItemId = rs.getInt("menu_item_id"),
            ingredientId = rs.getInt("ingredient_id"),
            quantityNeeded = rs.getDouble("quantity_needed")
        )
    }
}

