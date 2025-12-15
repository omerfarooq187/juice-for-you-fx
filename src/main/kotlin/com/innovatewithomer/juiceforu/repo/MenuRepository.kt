package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.MenuItem

object MenuItemRepository {

    fun getAllMenuItems(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        Database.getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM menu_items")
                while (rs.next()) {
                    items.add(
                        MenuItem(
                            id = rs.getInt("id"),
                            category = rs.getString("category"),
                            name = rs.getString("name"),
                            size = rs.getString("size"),
                            price = rs.getDouble("price")
                        )
                    )
                }
            }
        }
        return items
    }

    fun addMenuItem(item: MenuItem) {
        Database.getConnection().use { conn ->
            val sql = "INSERT INTO menu_items (category, name, size, price) VALUES (?, ?, ?, ?)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, item.category)
                stmt.setString(2, item.name)
                stmt.setString(3, item.size)
                stmt.setDouble(4, item.price)
                stmt.executeUpdate()
            }
        }
    }

    fun updateMenuItem(item: MenuItem) {
        Database.getConnection().use { conn ->
            val sql = "UPDATE menu_items SET category = ?, name = ?, size = ?, price = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, item.category)
                stmt.setString(2, item.name)
                stmt.setString(3, item.size)
                stmt.setDouble(4, item.price)
                stmt.setInt(5, item.id!!)
                stmt.executeUpdate()
            }
        }
    }


    fun deleteMenuItem(id: Int) {
        val sql = "DELETE FROM menu_items WHERE id = ?"
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, id)
                stmt.executeUpdate()
            }
        }
    }

}
