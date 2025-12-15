package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.RecipeItem
import java.sql.ResultSet

class RecipeRepository {

    fun addRecipe(recipe: RecipeItem) {
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement(
                "INSERT INTO recipes (menu_item_id, ingredient_id, quantity_needed) VALUES (?, ?, ?)"
            )
            stmt.setInt(1, recipe.menuItemId)
            stmt.setInt(2, recipe.ingredientId)
            stmt.setDouble(3, recipe.quantityNeeded)
            stmt.executeUpdate()
        }
    }

    fun updateRecipe(recipe: RecipeItem) {
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement(
                "UPDATE recipes SET ingredient_id=?, quantity_needed=? WHERE id=?"
            )
            stmt.setInt(1, recipe.ingredientId)
            stmt.setDouble(2, recipe.quantityNeeded)
            stmt.setInt(3, recipe.id)
            stmt.executeUpdate()
        }
    }


    fun deleteRecipe(id: Int) {
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement("DELETE FROM recipes WHERE id=?")
            stmt.setInt(1, id)
            stmt.executeUpdate()
        }
    }


    fun getRecipesForMenuItem(menuItemId: Int): List<RecipeItem> {
        val list = mutableListOf<RecipeItem>()
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement("SELECT * FROM recipes WHERE menu_item_id=?")
            stmt.setInt(1, menuItemId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(resultToRecipe(rs))
            }
        }
        return list
    }

    private fun resultToRecipe(rs: ResultSet): RecipeItem {
        return RecipeItem(
            id = rs.getInt("id"), // ✅ get recipe id
            menuItemId = rs.getInt("menu_item_id"),
            ingredientId = rs.getInt("ingredient_id"),
            quantityNeeded = rs.getDouble("quantity_needed")
        )
    }

}
