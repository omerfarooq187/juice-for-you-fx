package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.MenuItem
import java.sql.Connection

object MenuItemRepository {

    fun getAllMenuItems(conn: Connection? = null): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        val sql = "SELECT * FROM menu_items ORDER BY category ASC, name ASC"
        val execute = { c: Connection ->
            c.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        items.add(
                            MenuItem(
                                id = rs.getInt("id"),
                                category = rs.getString("category") ?: "",
                                name = rs.getString("name") ?: "",
                                size = rs.getString("size") ?: "",
                                price = rs.getDouble("price")
                            )
                        )
                    }
                }
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
        return items
    }

    fun addMenuItem(item: MenuItem, conn: Connection? = null) {
        val sql = "INSERT INTO menu_items (category, name, size, price) VALUES (?, ?, ?, ?)"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setString(1, item.category)
                stmt.setString(2, item.name)
                stmt.setString(3, item.size)
                stmt.setDouble(4, item.price)
                stmt.executeUpdate()
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    fun updateMenuItem(item: MenuItem, conn: Connection? = null) {
        val sql = "UPDATE menu_items SET category = ?, name = ?, size = ?, price = ? WHERE id = ?"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setString(1, item.category)
                stmt.setString(2, item.name)
                stmt.setString(3, item.size)
                stmt.setDouble(4, item.price)
                stmt.setInt(5, item.id ?: 0)
                stmt.executeUpdate()
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    fun deleteMenuItem(id: Int, conn: Connection? = null) {
        val sql = "DELETE FROM menu_items WHERE id = ?"
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
}

