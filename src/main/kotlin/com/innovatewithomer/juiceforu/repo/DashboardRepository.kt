package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.BusinessDay

object DashboardRepository {

    // 🟢 Today's total sales
    fun getTodaySales(): Int {
        val sql = """
        SELECT IFNULL(SUM(total), 0) AS total_sales
        FROM orders
        WHERE created_at >= ? AND created_at < ?
        AND order_status != 'CANCELLED'
    """
        val range = BusinessDay.today()

        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, range.startInclusive)
                stmt.setLong(2, range.endExclusive)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getInt("total_sales") else 0
            }
        }
    }

    fun getTodayOrders(): Int {
        val sql = """
        SELECT COUNT(*) AS order_count
        FROM orders
        WHERE created_at >= ? AND created_at < ?
        AND order_status != 'CANCELLED'
    """
        val range = BusinessDay.today()

        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, range.startInclusive)
                stmt.setLong(2, range.endExclusive)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getInt("order_count") else 0
            }
        }
    }

    // 🟢 Top selling items (limit 5)
    fun getTopSellingItems(limit: Int = 5): List<Pair<String, Int>> {
        val sql = """
            SELECT item_name, SUM(quantity) AS count
            FROM order_items i
            JOIN orders o ON o.id = i.order_id
            WHERE o.created_at >= ? AND o.created_at < ?
              AND o.order_status != 'CANCELLED'
            GROUP BY item_name
            ORDER BY count DESC
            LIMIT ?
        """
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val range = BusinessDay.today()
                stmt.setLong(1, range.startInclusive)
                stmt.setLong(2, range.endExclusive)
                stmt.setInt(3, limit)
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
            WHERE created_at >= ? AND created_at < ?
            ORDER BY created_at DESC
            LIMIT ?
        """
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val range = BusinessDay.today()
                stmt.setLong(1, range.startInclusive)
                stmt.setLong(2, range.endExclusive)
                stmt.setInt(3, limit)
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

}
