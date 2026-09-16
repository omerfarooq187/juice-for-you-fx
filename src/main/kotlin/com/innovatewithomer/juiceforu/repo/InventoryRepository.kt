package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.InventoryItem
import java.sql.Connection
import java.sql.ResultSet

class InventoryRepository {

    // --- CREATE ---
    fun addItem(item: InventoryItem, conn: Connection? = null) {
        val sql = "INSERT INTO inventory_items (name, unit, quantity, reorder_level) VALUES (?, ?, ?, ?)"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setString(1, item.name)
                stmt.setString(2, item.unit)
                stmt.setDouble(3, item.quantity)
                stmt.setDouble(4, item.reorderLevel)
                stmt.executeUpdate()
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    // --- UPDATE ---
    fun updateItem(item: InventoryItem, conn: Connection? = null) {
        val sql = "UPDATE inventory_items SET name=?, unit=?, quantity=?, reorder_level=? WHERE id=?"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setString(1, item.name)
                stmt.setString(2, item.unit)
                stmt.setDouble(3, item.quantity)
                stmt.setDouble(4, item.reorderLevel)
                stmt.setInt(5, item.id)
                stmt.executeUpdate()
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    // --- DELETE ---
    fun deleteItem(id: Int, conn: Connection? = null) {
        val sql = "DELETE FROM inventory_items WHERE id=?"
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

    // --- READ ---
    fun getAllItems(conn: Connection? = null): List<InventoryItem> {
        val list = mutableListOf<InventoryItem>()
        val sql = "SELECT * FROM inventory_items ORDER BY name ASC"
        val execute = { c: Connection ->
            c.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        list.add(resultToItem(rs))
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

    fun getItemById(id: Int, conn: Connection? = null): InventoryItem? {
        val sql = "SELECT * FROM inventory_items WHERE id=?"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) resultToItem(rs) else null
                }
            }
        }
        return if (conn != null) execute(conn) else Database.getConnection().use { execute(it) }
    }

    // --- STOCK ADJUSTMENT ---
    fun adjustStock(id: Int, change: Double, conn: Connection? = null) {
        val sql = "UPDATE inventory_items SET quantity = quantity + ? WHERE id=? AND quantity + ? >= 0"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setDouble(1, change)
                stmt.setInt(2, id)
                stmt.setDouble(3, change)
                if (stmt.executeUpdate() == 0) {
                    throw IllegalStateException("Inventory item #$id does not exist or has insufficient stock.")
                }
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    // --- MAPPER ---
    private fun resultToItem(rs: ResultSet): InventoryItem {
        return InventoryItem(
            id = rs.getInt("id"),
            name = rs.getString("name") ?: "",
            unit = rs.getString("unit") ?: "",
            quantity = rs.getDouble("quantity"),
            reorderLevel = rs.getDouble("reorder_level")
        )
    }
}
