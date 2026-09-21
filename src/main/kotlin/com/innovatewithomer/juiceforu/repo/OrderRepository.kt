package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.BusinessDay
import com.innovatewithomer.juiceforu.StockQuantity
import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.models.OrderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.Statement
import java.math.BigDecimal

class OrderRepository {

    /**
     * Atomically saves a brand new order, its items, and applies inventory deductions.
     * All operations execute in a single ACID transaction.
     */
    suspend fun saveOrderAtomic(
        order: Order,
        items: List<OrderItem>,
        inventoryDeductions: List<Pair<Int, Double>>
    ): Order = withContext(Dispatchers.IO) {
        Database.transaction { conn ->
            val usage = normalizeInventoryUsage(inventoryDeductions)
            val orderId = insertOrderInternal(conn, order)
            for (item in items) {
                insertOrderItemInternal(conn, item.copy(orderId = orderId))
            }
            for ((ingredientId, qty) in usage) {
                if (qty > 0) {
                    adjustInventoryStockInternal(conn, ingredientId, -qty)
                }
            }
            recordInventoryUsage(conn, orderId, usage)
            val savedOrder = getOrderByIdInternal(conn, orderId)
                ?: throw IllegalStateException("Order #$orderId could not be retrieved after atomic save.")
            savedOrder
        }
    }

    /**
     * Atomically updates an existing order:
     * 1. Updates order metadata.
     * 2. Restores inventory for removed/previous items.
     * 3. Replaces order items.
     * 4. Deducts inventory for updated items.
     */
    suspend fun updateOrderAtomic(
        order: Order,
        items: List<OrderItem>,
        inventoryRestores: List<Pair<Int, Double>>,
        inventoryDeductions: List<Pair<Int, Double>>
    ): Order = withContext(Dispatchers.IO) {
        Database.transaction { conn ->
            val usage = normalizeInventoryUsage(inventoryDeductions)
            val restores = if (hasRecordedInventoryUsage(conn, order.id))
                getRecordedInventoryUsage(conn, order.id) else normalizeInventoryUsage(inventoryRestores)
            updateOrderInternal(conn, order)

            // 1. Restore previous inventory
            for ((ingredientId, qty) in restores) {
                if (qty > 0 && inventoryItemExists(conn, ingredientId)) {
                    adjustInventoryStockInternal(conn, ingredientId, qty)
                }
            }

            // 2. Replace order items
            deleteOrderItemsInternal(conn, order.id)
            for (item in items) {
                insertOrderItemInternal(conn, item.copy(orderId = order.id))
            }

            // 3. Deduct new inventory
            for ((ingredientId, qty) in usage) {
                if (qty > 0) {
                    adjustInventoryStockInternal(conn, ingredientId, -qty)
                }
            }
            recordInventoryUsage(conn, order.id, usage)

            val updatedOrder = getOrderByIdInternal(conn, order.id)
                ?: throw IllegalStateException("Order #${order.id} could not be retrieved after atomic update.")
            updatedOrder
        }
    }

    // ✅ Insert new order (standalone)
    suspend fun insertOrder(order: Order): Int = withContext(Dispatchers.IO) {
        Database.getConnection().use { conn ->
            insertOrderInternal(conn, order)
        }
    }

    private fun insertOrderInternal(conn: Connection, order: Order): Int {
        val sql = """
            INSERT INTO orders (
                total, order_type, order_status, delivery_charges, service_charges,
                discount_percent, discount_amount, created_at, is_edited, address, phone, order_no
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setInt(1, order.total)
            stmt.setString(2, order.orderType)
            stmt.setString(3, order.orderStatus ?: "PENDING")
            stmt.setDouble(4, order.deliveryCharges.toDouble())
            stmt.setDouble(5, order.serviceCharges.toDouble())
            stmt.setDouble(6, order.discountPercent)
            stmt.setDouble(7, order.discountAmount)
            stmt.setLong(8, if (order.createdAt > 0) order.createdAt else System.currentTimeMillis())
            stmt.setInt(9, if (order.isEdited) 1 else 0)
            stmt.setString(10, order.customerAddress)
            stmt.setString(11, order.customerPhone)
            stmt.setInt(12, order.orderNo)
            stmt.executeUpdate()

            stmt.generatedKeys.use { rs ->
                if (rs.next()) {
                    return rs.getInt(1)
                }
            }
        }
        throw RuntimeException("⚠️ Failed to insert order: no generated key returned.")
    }

    // ✅ Update existing order (standalone)
    suspend fun updateOrder(order: Order): Boolean = withContext(Dispatchers.IO) {
        Database.getConnection().use { conn ->
            updateOrderInternal(conn, order)
        }
    }

    private fun updateOrderInternal(conn: Connection, order: Order): Boolean {
        val sql = """
            UPDATE orders 
            SET total = ?, order_type = ?, order_status = ?, delivery_charges = ?, service_charges = ?, 
                discount_percent = ?, discount_amount = ?, is_edited = 1, address = ?, phone = ?, order_no = ?
            WHERE id = ?
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, order.total)
            stmt.setString(2, order.orderType)
            stmt.setString(3, order.orderStatus ?: "PENDING")
            stmt.setDouble(4, order.deliveryCharges.toDouble())
            stmt.setDouble(5, order.serviceCharges.toDouble())
            stmt.setDouble(6, order.discountPercent)
            stmt.setDouble(7, order.discountAmount)
            stmt.setString(8, order.customerAddress)
            stmt.setString(9, order.customerPhone)
            stmt.setInt(10, order.orderNo)
            stmt.setInt(11, order.id)
            return stmt.executeUpdate() > 0
        }
    }

    // ✅ Fetch all orders
    suspend fun getAllOrders(): List<Order> = withContext(Dispatchers.IO) {
        val orders = mutableListOf<Order>()
        val sql = "SELECT * FROM orders ORDER BY created_at DESC"

        Database.getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    val id = rs.getInt("id")
                    orders.add(
                        Order(
                            id = id,
                            orderNo = rs.getInt("order_no"),
                            total = rs.getInt("total"),
                            createdAt = rs.getLong("created_at"),
                            items = getOrderItemsInternal(conn, id),
                            orderType = rs.getString("order_type") ?: "Takeaway",
                            orderStatus = rs.getString("order_status") ?: "PENDING",
                            deliveryCharges = rs.getInt("delivery_charges"),
                            serviceCharges = rs.getInt("service_charges"),
                            discountPercent = rs.getDouble("discount_percent"),
                            discountAmount = rs.getDouble("discount_amount"),
                            isEdited = rs.getInt("is_edited") == 1,
                            customerAddress = rs.getString("address"),
                            customerPhone = rs.getString("phone")
                        )
                    )
                }
            }
        }
        orders
    }

    // ✅ Generate next order number safely
    suspend fun getNextOrderNo(): Int = withContext(Dispatchers.IO) {
        val todayDate = BusinessDay.currentDate().toString()

        Database.transaction { conn ->
            var lastResetDate: String? = null
            var lastOrderNo = 200

            conn.prepareStatement("SELECT last_reset_date, last_order_no FROM order_number_tracker ORDER BY id DESC LIMIT 1").use { stmt ->
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    lastResetDate = rs.getString("last_reset_date")
                    lastOrderNo = rs.getInt("last_order_no")
                }
            }

            if (lastResetDate?.let { it < todayDate } ?: true) {
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
                val rows = updateStmt.executeUpdate()
                if (rows == 0) {
                    // If no row existed to update, insert first tracking record
                    conn.prepareStatement("INSERT INTO order_number_tracker (last_reset_date, last_order_no) VALUES (?, ?)").use { ins ->
                        ins.setString(1, todayDate)
                        ins.setInt(2, nextOrderNo)
                        ins.executeUpdate()
                    }
                }
            }

            nextOrderNo
        }
    }

    fun updateOrderStatus(orderId: Int, status: String) {
        val sql = "UPDATE orders SET order_status = ? WHERE id = ?"
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status)
                stmt.setInt(2, orderId)
                stmt.executeUpdate()
            }
        }
    }

    fun insertOrderItem(item: OrderItem) {
        Database.getConnection().use { conn ->
            insertOrderItemInternal(conn, item)
        }
    }

    private fun insertOrderItemInternal(conn: Connection, item: OrderItem) {
        val sql = """
            INSERT INTO order_items (order_id, menu_item_id, item_name, category, size, price, quantity) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
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

    private fun adjustInventoryStockInternal(conn: Connection, ingredientId: Int, change: Double) {
        val sql = "UPDATE inventory_items SET quantity = ROUND(quantity + ?, 2) WHERE id = ? AND ROUND(quantity + ?, 2) >= 0"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setDouble(1, StockQuantity.round(change))
            stmt.setInt(2, ingredientId)
            stmt.setDouble(3, StockQuantity.round(change))
            if (stmt.executeUpdate() == 0) {
                throw IllegalStateException("Insufficient inventory for ingredient #$ingredientId.")
            }
        }
    }

    private fun hasRecordedInventoryUsage(conn: Connection, orderId: Int): Boolean =
        conn.prepareStatement("SELECT inventory_usage_recorded FROM orders WHERE id = ?").use { stmt ->
            stmt.setInt(1, orderId)
            stmt.executeQuery().use { rows -> rows.next() && rows.getInt(1) == 1 }
        }

    private fun inventoryItemExists(conn: Connection, ingredientId: Int): Boolean =
        conn.prepareStatement("SELECT 1 FROM inventory_items WHERE id = ?").use { stmt ->
            stmt.setInt(1, ingredientId)
            stmt.executeQuery().use { it.next() }
        }

    private fun getRecordedInventoryUsage(conn: Connection, orderId: Int): List<Pair<Int, Double>> =
        conn.prepareStatement("SELECT ingredient_id, quantity FROM order_inventory_usage WHERE order_id = ?").use { stmt ->
            stmt.setInt(1, orderId)
            stmt.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.getInt(1) to rows.getDouble(2))
                }
            }
        }

    private fun recordInventoryUsage(conn: Connection, orderId: Int, usage: List<Pair<Int, Double>>) {
        conn.prepareStatement("DELETE FROM order_inventory_usage WHERE order_id = ?").use { stmt ->
            stmt.setInt(1, orderId)
            stmt.executeUpdate()
        }
        conn.prepareStatement("INSERT INTO order_inventory_usage (order_id, ingredient_id, quantity) VALUES (?, ?, ?)").use { stmt ->
            for ((ingredientId, amount) in usage) {
                if (amount <= 0) continue
                stmt.setInt(1, orderId)
                stmt.setInt(2, ingredientId)
                stmt.setDouble(3, amount)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        conn.prepareStatement("UPDATE orders SET inventory_usage_recorded = 1 WHERE id = ?").use { stmt ->
            stmt.setInt(1, orderId)
            stmt.executeUpdate()
        }
    }

    private fun normalizeInventoryUsage(usage: List<Pair<Int, Double>>): List<Pair<Int, Double>> =
        usage.groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (ingredientId, values) ->
                val total = values.fold(BigDecimal.ZERO) { sum, value -> sum.add(BigDecimal.valueOf(value)) }
                ingredientId to StockQuantity.round(total.toDouble())
            }.filter { it.second > 0 }

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
        Database.getConnection().use { conn ->
            getOrderByIdInternal(conn, orderId)
        }
    }

    private fun getOrderByIdInternal(conn: Connection, orderId: Int): Order? {
        val sql = "SELECT * FROM orders WHERE id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, orderId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return Order(
                    id = orderId,
                    orderNo = rs.getInt("order_no"),
                    total = rs.getInt("total"),
                    createdAt = rs.getLong("created_at"),
                    items = getOrderItemsInternal(conn, orderId),
                    orderType = rs.getString("order_type") ?: "Takeaway",
                    orderStatus = rs.getString("order_status") ?: "PENDING",
                    deliveryCharges = rs.getInt("delivery_charges"),
                    serviceCharges = rs.getInt("service_charges"),
                    discountPercent = rs.getDouble("discount_percent"),
                    discountAmount = rs.getDouble("discount_amount"),
                    isEdited = rs.getInt("is_edited") == 1,
                    customerAddress = rs.getString("address"),
                    customerPhone = rs.getString("phone")
                )
            }
        }
        return null
    }

    suspend fun getOrdersByDate(filter: String): List<Order> = withContext(Dispatchers.IO) {
        val orders = mutableListOf<Order>()
        val range = when (filter.lowercase()) {
            "today" -> BusinessDay.today()
            "weekly" -> BusinessDay.lastDays(7)
            "monthly" -> BusinessDay.thisMonth()
            else -> null
        }

        val sql = if (range != null) {
            "SELECT * FROM orders WHERE created_at >= ? AND created_at < ? ORDER BY created_at DESC"
        } else {
            "SELECT * FROM orders ORDER BY created_at DESC"
        }

        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                if (range != null) {
                    stmt.setLong(1, range.startInclusive)
                    stmt.setLong(2, range.endExclusive)
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
                            items = getOrderItemsInternal(conn, orderId),
                            orderType = rs.getString("order_type") ?: "Takeaway",
                            orderStatus = rs.getString("order_status") ?: "PENDING",
                            deliveryCharges = rs.getInt("delivery_charges"),
                            serviceCharges = rs.getInt("service_charges"),
                            discountPercent = rs.getDouble("discount_percent"),
                            discountAmount = rs.getDouble("discount_amount"),
                            isEdited = rs.getInt("is_edited") == 1,
                            customerAddress = rs.getString("address"),
                            customerPhone = rs.getString("phone")
                        )
                    )
                }
            }
        }
        orders
    }

    suspend fun getOrderItems(orderId: Int): List<OrderItem> = withContext(Dispatchers.IO) {
        Database.getConnection().use { conn ->
            getOrderItemsInternal(conn, orderId)
        }
    }

    private fun getOrderItemsInternal(conn: Connection, orderId: Int): List<OrderItem> {
        val items = mutableListOf<OrderItem>()
        val sql = "SELECT * FROM order_items WHERE order_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, orderId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                items.add(
                    OrderItem(
                        id = rs.getInt("id"),
                        orderId = rs.getInt("order_id"),
                        menuItemId = rs.getInt("menu_item_id"),
                        itemName = rs.getString("item_name") ?: "",
                        category = rs.getString("category") ?: "",
                        size = rs.getString("size") ?: "",
                        price = rs.getDouble("price"),
                        quantity = rs.getInt("quantity")
                    )
                )
            }
        }
        return items
    }

    suspend fun updateOrderDiscountAndTotal(
        orderId: Int,
        discountPercent: Double,
        discountAmount: Double,
        total: Double
    ): Boolean = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE orders 
            SET discount_percent = ?, discount_amount = ?, total = ?, is_edited = 1
            WHERE id = ?
        """.trimIndent()
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setDouble(1, discountPercent)
                stmt.setDouble(2, discountAmount)
                stmt.setDouble(3, total)
                stmt.setInt(4, orderId)
                stmt.executeUpdate() > 0
            }
        }
    }

    suspend fun deleteOrderItems(orderId: Int): Boolean = withContext(Dispatchers.IO) {
        Database.getConnection().use { conn ->
            deleteOrderItemsInternal(conn, orderId)
        }
    }

    private fun deleteOrderItemsInternal(conn: Connection, orderId: Int): Boolean {
        val sql = "DELETE FROM order_items WHERE order_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, orderId)
            return stmt.executeUpdate() >= 0
        }
    }

    suspend fun getSalesSummary(): Map<String, Double> = withContext(Dispatchers.IO) {
        val sales = mutableMapOf("today" to 0.0, "weekly" to 0.0, "monthly" to 0.0)

        val ranges = mapOf(
            "today" to BusinessDay.today(),
            "weekly" to BusinessDay.lastDays(7),
            "monthly" to BusinessDay.thisMonth()
        )

        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT IFNULL(SUM(total), 0) AS total FROM orders WHERE created_at >= ? AND created_at < ? AND order_status != 'CANCELLED'"
            ).use { stmt ->
                for ((key, range) in ranges) {
                    stmt.setLong(1, range.startInclusive)
                    stmt.setLong(2, range.endExclusive)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) sales[key] = rs.getDouble("total")
                    }
                }
            }
        }

        sales
    }

    suspend fun peekNextOrderNo(): Int = withContext(Dispatchers.IO) {
        val sql = "SELECT last_reset_date, last_order_no FROM order_number_tracker ORDER BY id DESC LIMIT 1"
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                if (rs.next() && (rs.getString("last_reset_date") ?: "") >= BusinessDay.currentDate().toString())
                    rs.getInt("last_order_no") + 1
                else 201
            }
        }
    }
}
