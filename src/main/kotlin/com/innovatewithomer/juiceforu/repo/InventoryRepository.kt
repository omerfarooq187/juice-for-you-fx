package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.InventoryItem
import java.sql.ResultSet

class InventoryRepository {

    // --- CREATE ---
    fun addItem(item: InventoryItem) {
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement(
                "INSERT INTO inventory_items (name, unit, quantity, reorder_level) VALUES (?, ?, ?, ?)"
            )
            stmt.setString(1, item.name)
            stmt.setString(2, item.unit)
            stmt.setDouble(3, item.quantity)
            stmt.setDouble(4, item.reorderLevel)
            stmt.executeUpdate()
        }
    }

    // --- UPDATE ---
    fun updateItem(item: InventoryItem) {
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement(
                "UPDATE inventory_items SET name=?, unit=?, quantity=?, reorder_level=? WHERE id=?"
            )
            stmt.setString(1, item.name)
            stmt.setString(2, item.unit)
            stmt.setDouble(3, item.quantity)
            stmt.setDouble(4, item.reorderLevel)
            stmt.setInt(5, item.id)
            stmt.executeUpdate()
        }
    }

    // --- DELETE ---
    fun deleteItem(id: Int) {
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement("DELETE FROM inventory_items WHERE id=?")
            stmt.setInt(1, id)
            stmt.executeUpdate()
        }
    }

    // --- READ ---
    fun getAllItems(): List<InventoryItem> {
        val list = mutableListOf<InventoryItem>()
        Database.getConnection().use { conn ->
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("SELECT * FROM inventory_items")
            while (rs.next()) {
                list.add(resultToItem(rs))
            }
        }
        return list
    }

    fun getItemById(id: Int): InventoryItem? {
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement("SELECT * FROM inventory_items WHERE id=?")
            stmt.setInt(1, id)
            val rs = stmt.executeQuery()
            return if (rs.next()) resultToItem(rs) else null
        }
    }

    // --- STOCK ADJUSTMENT ---
    fun adjustStock(id: Int, change: Double) {
        Database.getConnection().use { conn ->
            val stmt = conn.prepareStatement(
                "UPDATE inventory_items SET quantity = quantity + ? WHERE id=?"
            )
            stmt.setDouble(1, change)
            stmt.setInt(2, id)
            stmt.executeUpdate()
        }
    }

    // --- MAPPER ---
    private fun resultToItem(rs: ResultSet): InventoryItem {
        return InventoryItem(
            id = rs.getInt("id"),
            name = rs.getString("name"),
            unit = rs.getString("unit"),
            quantity = rs.getDouble("quantity"),
            reorderLevel = rs.getDouble("reorder_level")
        )
    }
}
