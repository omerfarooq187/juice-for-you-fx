package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import java.sql.ResultSet
import java.util.Calendar

object DashboardRepository {

    // 🟢 Today's total sales
    fun getTodaySales(): Int {
        val sql = """
        SELECT IFNULL(SUM(total), 0) AS total_sales
        FROM orders
        WHERE created_at BETWEEN ? AND ?
        AND order_status != 'CANCELLED'
    """
        val start = getStartOfDayMillis()
        val end = getEndOfDayMillis()

        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, start)
                stmt.setLong(2, end)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getInt("total_sales") else 0
            }
        }
    }

    fun getTodayOrders(): Int {
        val sql = """
        SELECT COUNT(*) AS order_count
        FROM orders
        WHERE created_at BETWEEN ? AND ?
        AND order_status != 'CANCELLED'
    """
        val start = getStartOfDayMillis()
        val end = getEndOfDayMillis()

        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, start)
                stmt.setLong(2, end)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getInt("order_count") else 0
            }
        }
    }

    // 🟢 Top selling items (limit 5)
    fun getTopSellingItems(limit: Int = 5): List<Pair<String, Int>> {
        val sql = """
            SELECT item_name, SUM(quantity) AS count
            FROM order_items
            GROUP BY item_name
            ORDER BY count DESC
            LIMIT ?
        """
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                val rs = stmt.executeQuery()
                val items = mutableListOf<Pair<String, Int>>()
                while (rs.next()) {
                    items.add(rs.getString("item_name") to rs.getInt("count"))
                }
                return items
            }
        }
    }

    // 🟢 Recent orders (last 10)
    fun getRecentOrders(limit: Int = 10): List<Triple<Int, Int, String>> {
        val sql = """
            SELECT id, total, created_at
            FROM orders
            ORDER BY created_at DESC
            LIMIT ?
        """
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                val rs = stmt.executeQuery()
                val orders = mutableListOf<Triple<Int, Int, String>>()
                while (rs.next()) {
                    orders.add(
                        Triple(
                            rs.getInt("id"),
                            rs.getInt("total"),
                            rs.getString("created_at")
                        )
                    )
                }
                return orders
            }
        }
    }

    private fun getStartOfDayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getEndOfDayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

}
