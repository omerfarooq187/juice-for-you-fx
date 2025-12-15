package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.models.OrderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager.getConnection
import java.sql.Statement
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class OrderRepository {
    // ✅ Insert new order
    suspend fun insertOrder(order: Order): Int = withContext(Dispatchers.IO) {
        val sql = """
        INSERT INTO orders (
            total, order_type, order_status, delivery_charges, service_charges,
            discount_percent, discount_amount, created_at, is_edited, address, phone, order_no
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
    """
        Database.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
                    stmt.setInt(1, order.total)
                    stmt.setString(2, order.orderType)
                    stmt.setString(3, order.orderStatus)
                    stmt.setDouble(4, order.deliveryCharges.toDouble())
                    stmt.setDouble(5, order.serviceCharges.toDouble())
                    stmt.setDouble(6, order.discountPercent)
                    stmt.setDouble(7, order.discountAmount)
                    stmt.setLong(8, System.currentTimeMillis())
                    stmt.setString(9, order.customerAddress)
                    stmt.setString(10, order.customerPhone)
                    stmt.setInt(11, order.orderNo)
                    stmt.executeUpdate()

                    stmt.generatedKeys.use { rs ->
                        if (rs.next()) {
                            val id = rs.getInt(1)
                            conn.commit()
                            return@withContext id
                        }
                    }
                }
                conn.rollback()
                throw RuntimeException("⚠️ Failed to insert order: no generated key found.")
            } catch (e: Exception) {
                conn.rollback()
                println("❌ insertOrder failed: ${e.message}")
                throw e
            }
        }
    }


    // ✅ Update existing order
    suspend fun updateOrder(order: Order): Boolean = withContext(Dispatchers.IO) {
        val sql = """
        UPDATE orders 
        SET total = ?, order_type = ?, order_status = ?, delivery_charges = ?, service_charges = ?, 
            discount_percent = ?, discount_amount = ?, is_edited = 1, address = ?, phone = ?, order_no = ?
        WHERE id = ?
    """
        Database.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, order.total)
                    stmt.setString(2, order.orderType)
                    stmt.setString(3, order.orderStatus)
                    stmt.setDouble(4, order.deliveryCharges.toDouble())
                    stmt.setDouble(5, order.serviceCharges.toDouble())
                    stmt.setDouble(6, order.discountPercent)
                    stmt.setDouble(7, order.discountAmount)
                    stmt.setString(8, order.customerAddress)
                    stmt.setString(9, order.customerPhone)
                    stmt.setInt(10, order.orderNo)
                    stmt.setInt(11, order.id)
                    stmt.executeUpdate()
                }
                conn.commit()
                true
            } catch (e: Exception) {
                conn.rollback()
                println("❌ updateOrder failed: ${e.message}")
                false
            }
        }
    }


    // ✅ Fetch all orders
    suspend fun getAllOrders(): List<Order> = withContext(Dispatchers.IO) {
        val conn = Database.getConnection()
        val orders = mutableListOf<Order>()
        val sql = "SELECT * FROM orders ORDER BY created_at DESC"

        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            while (rs.next()) {
                val id = rs.getInt("id")
                orders.add(
                    Order(
                        id = id,
                        total = rs.getInt("total"),
                        createdAt = rs.getLong("created_at"),
                        items = getOrderItems(id),
                        orderType = rs.getString("order_type"),
                        orderStatus = rs.getString("order_status"),
                        deliveryCharges = rs.getInt("delivery_charges"),
                        serviceCharges = rs.getInt("service_charges"),
                        discountPercent = rs.getDouble("discount_percent"),
                        discountAmount = rs.getDouble("discount_amount"),
                        isEdited = rs.getInt("is_edited") == 1,
                        customerAddress = rs.getString("address"),
                        customerPhone = rs.getString("phone"),
                        orderNo = rs.getInt("order_no")
                    )
                )
            }
        }
        return@withContext orders
    }

    // ✅ Helper — get order items for a specific order
//    private fun getOrderItems(conn: Connection, orderId: Int): List<OrderItem> {
//        val items = mutableListOf<OrderItem>()
//        val sql = "SELECT * FROM order_items WHERE order_id = ?"
//        conn.prepareStatement(sql).use { stmt ->
//            stmt.setInt(1, orderId)
//            val rs = stmt.executeQuery()
//            while (rs.next()) {
//                items.add(
//                    OrderItem(
//                        id = rs.getInt("id"),
//                        orderId = rs.getInt("order_id"),
//                        menuItemId = rs.getInt("menu_item_id"),
//                        itemName = rs.getString("item_name"),
//                        category = rs.getString("category"),
//                        size = rs.getString("size"),
//                        price = rs.getDouble("price"),
//                        quantity = rs.getInt("quantity")
//                    )
//                )
//            }
//        }
//        return items
//    }

    // ✅ Generate next order number safely (no double increment)
    suspend fun getNextOrderNo(): Int = withContext(Dispatchers.IO) {
        val conn = Database.getConnection()
        val todayDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS order_number_tracker (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    last_reset_date TEXT,
                    last_order_no INTEGER
                )
                """
            )
        }

        var lastResetDate: String? = null
        var lastOrderNo = 200

        conn.prepareStatement("SELECT id, last_reset_date, last_order_no FROM order_number_tracker ORDER BY id DESC LIMIT 1").use { stmt ->
            val rs = stmt.executeQuery()
            if (rs.next()) {
                lastResetDate = rs.getString("last_reset_date")
                lastOrderNo = rs.getInt("last_order_no")
            }
        }

        if (lastResetDate != todayDate && currentHour >= 2) {
            conn.prepareStatement(
                "INSERT INTO order_number_tracker (last_reset_date, last_order_no) VALUES (?, ?)"
            ).use { insertStmt ->
                insertStmt.setString(1, todayDate)
                insertStmt.setInt(2, 200)
                insertStmt.executeUpdate()
            }
            lastOrderNo = 200
        }

        val nextOrderNo = lastOrderNo + 1
        conn.prepareStatement(
            "UPDATE order_number_tracker SET last_order_no = ? WHERE id = (SELECT id FROM order_number_tracker ORDER BY id DESC LIMIT 1)"
        ).use { updateStmt ->
            updateStmt.setInt(1, nextOrderNo)
            updateStmt.executeUpdate()
        }

        return@withContext nextOrderNo
    }

    fun updateOrderStatus(orderId: Int, status: String) {
        val sql = "UPDATE orders SET order_status = ? WHERE id = ?"
        Database.getConnection().use { conn->
            conn.prepareStatement(sql).use { stmt->
                stmt.setString(1, status)
                stmt.setInt(2, orderId)
                stmt.executeUpdate()
                stmt.close()
            }
        }
    }


    fun insertOrderItem(item: OrderItem) {
        val sql = """
        INSERT INTO order_items (order_id, menu_item_id, item_name, category, size, price, quantity) 
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, item.orderId)
                stmt.setInt(2, item.menuItemId)
                stmt.setString(3, item.itemName)
                stmt.setString(4, item.category)
                stmt.setString(5, item.size)
                stmt.setDouble(6, item.price)
                stmt.setInt(7, item.quantity)
                stmt.executeUpdate()
            }
        }
    }

    private fun getStartAndEndOfToday(): Pair<Long, Long> {
        val cal = Calendar.getInstance() // local timezone
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return start to end
    }

    private fun getStartAndEndOfLastDays(days: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance() // ✅ no UTC here
        val end = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val start = cal.timeInMillis
        return start to end
    }

    private fun getStartAndEndOfThisMonth(): Pair<Long, Long> {
        val cal = Calendar.getInstance() // ✅ local timezone
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        val calEnd = Calendar.getInstance()
        calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
        calEnd.set(Calendar.HOUR_OF_DAY, 23)
        calEnd.set(Calendar.MINUTE, 59)
        calEnd.set(Calendar.SECOND, 59)
        calEnd.set(Calendar.MILLISECOND, 999)
        val end = calEnd.timeInMillis

        return start to end
    }





    fun getNextOrderId(): Int {
        val sql = "SELECT IFNULL(MAX(id), 0) + 1 FROM orders"
        Database.getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                if (rs.next()) return rs.getInt(1)
            }
        }
        return 1
    }

    suspend fun getOrderById(orderId: Int): Order? = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM orders WHERE id = ?"
        val conn = Database.getConnection()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, orderId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return@withContext Order(
                    id = orderId,
                    total = rs.getInt("total"),
                    createdAt = rs.getLong("created_at"),
                    items = getOrderItems(orderId),
                    orderType = rs.getString("order_type"),
                    orderStatus = rs.getString("order_status"),
                    deliveryCharges = rs.getInt("delivery_charges"),
                    serviceCharges = rs.getInt("service_charges"),
                    discountPercent = rs.getDouble("discount_percent"),
                    discountAmount = rs.getDouble("discount_amount"),
                    isEdited = rs.getInt("is_edited") == 1,
                    customerAddress = rs.getString("address"),
                    customerPhone = rs.getString("phone"),
                    orderNo = rs.getInt("order_no")
                )
            }
        }
        null
    }

    suspend fun getOrdersByDate(filter: String): List<Order> = withContext(Dispatchers.IO) {
        val orders = mutableListOf<Order>()
        val (start, end) = when (filter.lowercase()) {
            "today" -> getStartAndEndOfToday()
            "weekly" -> getStartAndEndOfLastDays(7)
            "monthly" -> getStartAndEndOfThisMonth()
            else -> null to null
        }

        val sql = if (start != null && end != null) {
            "SELECT * FROM orders WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC"
        } else {
            "SELECT * FROM orders ORDER BY created_at DESC"
        }

        val conn = Database.getConnection()
        conn.prepareStatement(sql).use { stmt ->
            if (start != null && end != null) {
                stmt.setLong(1, start)
                stmt.setLong(2, end)
            }
            val rs = stmt.executeQuery()
            while (rs.next()) {
                val orderId = rs.getInt("id")
                orders.add(
                    Order(
                        id = orderId,
                        orderNo = rs.getInt("order_no"),
                        total = rs.getInt("total"),
                        createdAt = rs.getLong("created_at"),
                        items = getOrderItems(orderId),
                        orderType = rs.getString("order_type"),
                        orderStatus = rs.getString("order_status")
                    )
                )
            }
        }
        orders
    }

    suspend fun getOrderItems(orderId: Int): List<OrderItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<OrderItem>()
        val sql = "SELECT * FROM order_items WHERE order_id = ?"
        val conn = Database.getConnection()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, orderId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                items.add(
                    OrderItem(
                        id = rs.getInt("id"),
                        orderId = rs.getInt("order_id"),
                        menuItemId = rs.getInt("menu_item_id"),
                        itemName = rs.getString("item_name"),
                        category = rs.getString("category"),
                        size = rs.getString("size"),
                        price = rs.getDouble("price"),
                        quantity = rs.getInt("quantity")
                    )
                )
            }
        }
        items
    }

    suspend fun updateOrderDiscountAndTotal(
        orderId: Int,
        discountPercent: Double,
        discountAmount: Double,
        total: Double
    ) = withContext(Dispatchers.IO) {
        val sql = """
        UPDATE orders 
        SET discount_percent = ?, discount_amount = ?, total = ?, is_edited = 1
        WHERE id = ?
    """
        Database.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setDouble(1, discountPercent)
                    stmt.setDouble(2, discountAmount)
                    stmt.setDouble(3, total)
                    stmt.setInt(4, orderId)
                    stmt.executeUpdate()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                println("❌ updateOrderDiscountAndTotal failed: ${e.message}")
            }
        }
    }


    suspend fun deleteOrderItems(orderId: Int) = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM order_items WHERE order_id = ?"
        Database.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, orderId)
                    stmt.executeUpdate()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                println("❌ deleteOrderItems failed: ${e.message}")
            }
        }
    }


    suspend fun getSalesSummary(): Map<String, Double> = withContext(Dispatchers.IO) {
        val sales = mutableMapOf("today" to 0.0, "weekly" to 0.0, "monthly" to 0.0)
        val conn = Database.getConnection()

        val queries = mapOf(
            "today" to """
            SELECT SUM(total) as total FROM orders
            WHERE DATE(created_at / 1000, 'unixepoch', 'localtime') = DATE('now', 'localtime')
              AND order_status != 'CANCELLED'
        """,
            "weekly" to """
            SELECT SUM(total) as total FROM orders
            WHERE DATE(created_at / 1000, 'unixepoch', 'localtime') 
                  >= DATE('now', '-6 days', 'localtime')
              AND order_status != 'CANCELLED'
        """,
            "monthly" to """
            SELECT SUM(total) as total FROM orders
            WHERE strftime('%Y-%m', created_at / 1000, 'unixepoch', 'localtime') = 
                  strftime('%Y-%m', 'now', 'localtime')
              AND order_status != 'CANCELLED'
        """
        )

        println("System default zone: " + ZoneId.systemDefault())
        println("Current millis: " + System.currentTimeMillis())


        conn.createStatement().use { stmt ->
            for ((key, sql) in queries) {
                stmt.executeQuery(sql).use { rs ->
                    sales[key] = rs.getDouble("total")
                }
            }
        }

        sales
    }


    suspend fun peekNextOrderNo(): Int = withContext(Dispatchers.IO) {
        val sql = "SELECT last_order_no FROM order_number_tracker ORDER BY id DESC LIMIT 1"
        val conn = Database.getConnection()
        conn.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            return@withContext if (rs.next()) rs.getInt("last_order_no") + 1 else 201
        }
    }
}
