package com.innovatewithomer.juiceforu

import java.sql.Connection
import java.sql.DriverManager

object Database {
    private const val DB_URL = "jdbc:sqlite:juiceforyou.db"

    fun getConnection(): Connection {
        return DriverManager.getConnection(DB_URL).apply {
            createStatement().use {
                it.execute("PRAGMA busy_timeout = 5000;")
                it.execute("PRAGMA journal_mode = WAL;")
            }
        }
    }
}

//object Database {
//    private const val DB_URL = "jdbc:sqlite:pizza_hut.db"
//
//    init {
////        createTables()
////        seedMenuItems()
//    }
//
//    private fun createTables() {
//        connection.use { conn ->
//            conn.createStatement().use { stmt ->
//                stmt.executeUpdate(
//                    """
//                CREATE TABLE IF NOT EXISTS customers (
//                    id INTEGER PRIMARY KEY AUTOINCREMENT,
//                    name TEXT NOT NULL,
//                    phone TEXT,
//                    email TEXT,
//                    type TEXT DEFAULT 'Regular'
//                )
//                """
//                )
//
//                stmt.executeUpdate(
//                    """
//                        CREATE TABLE IF NOT EXISTS orders (
//                            id INTEGER PRIMARY KEY AUTOINCREMENT,
//                            total INTEGER NOT NULL,
//                            order_type TEXT NOT NULL DEFAULT 'Takeaway',
//                            delivery_charges REAL DEFAULT 0,
//                            service_charges REAL DEFAULT 0,
//                            created_at BIGINT,
//                            is_edited INTEGER DEFAULT 0,
//                            address TEXT,
//                            phone TEXT
//                        )
//                        """
//                )
//
//
//                stmt.executeUpdate(
//                    """
//                                CREATE TABLE IF NOT EXISTS order_items (
//                id INTEGER PRIMARY KEY AUTOINCREMENT,
//                order_id INTEGER NOT NULL,
//                menu_item_id INTEGER,                -- ✅ link to menu_items
//                item_name TEXT NOT NULL,
//                category TEXT NOT NULL,
//                size TEXT NOT NULL,
//                price REAL NOT NULL,
//                quantity INTEGER NOT NULL DEFAULT 1,
//                FOREIGN KEY(order_id) REFERENCES orders(id),
//                FOREIGN KEY(menu_item_id) REFERENCES menu_items(id)
//            )
//
//
//                    """
//                )
//
//
//                // ✅ Menu items
//                stmt.executeUpdate(
//                    """
//                CREATE TABLE IF NOT EXISTS menu_items (
//                    id INTEGER PRIMARY KEY AUTOINCREMENT,
//                    category TEXT NOT NULL,
//                    name TEXT NOT NULL,
//                    size TEXT NOT NULL,
//                    price REAL NOT NULL
//                )
//                """
//                )
//
//                stmt.executeUpdate(
//                    """
//                CREATE TABLE IF NOT EXISTS inventory_items (
//                    id INTEGER PRIMARY KEY AUTOINCREMENT,
//                    name TEXT NOT NULL,
//                    unit TEXT NOT NULL,
//                    quantity REAL NOT NULL,
//                    reorder_level REAL NOT NULL
//                )
//                """
//                )
//
//                stmt.executeUpdate(
//                    """
//                CREATE TABLE IF NOT EXISTS recipes (
//                    id INTEGER PRIMARY KEY AUTOINCREMENT,
//                    menu_item_id INTEGER NOT NULL,
//                    ingredient_id INTEGER NOT NULL,
//                    quantity_needed REAL NOT NULL,
//                    FOREIGN KEY (menu_item_id) REFERENCES menu_items(id),
//                    FOREIGN KEY (ingredient_id) REFERENCES inventory_items(id)
//                )
//                """
//                )
//            }
//        }
//    }
//
//    private fun seedMenuItems() {
//        val menuItems = listOf(
//            // Ice Cream Shakes
//            arrayOf("Ice Cream Shakes", "Oreo", "R", 490),
//            arrayOf("Ice Cream Shakes", "Oreo", "L", 550),
//            arrayOf("Ice Cream Shakes", "Kitkat", "R", 490),
//            arrayOf("Ice Cream Shakes", "Kitkat", "L", 550),
//            arrayOf("Ice Cream Shakes", "M&M", "R", 490),
//            arrayOf("Ice Cream Shakes", "M&M", "L", 550),
//            arrayOf("Ice Cream Shakes", "Ice Cream", "R", 490),
//            arrayOf("Ice Cream Shakes", "Ice Cream", "L", 550),
//            arrayOf("Ice Cream Shakes", "Chocolate", "R", 490),
//            arrayOf("Ice Cream Shakes", "Chocolate", "L", 550),
//            // Smoothies
//            arrayOf("SMOOTHIES", "Apple", "R", 300),
//            arrayOf("SMOOTHIES", "Apple", "L", 350),
//            arrayOf("SMOOTHIES", "Banana", "R", 300),
//            arrayOf("SMOOTHIES", "Banana", "L", 350),
//            arrayOf("SMOOTHIES", "Strawberry", "R", 350),
//            arrayOf("SMOOTHIES", "Strawberry", "L", 400),
//            arrayOf("SMOOTHIES", "Pineapple", "R", 400),
//            arrayOf("SMOOTHIES", "Pineapple", "L", 450),
//            arrayOf("SMOOTHIES", "Peach", "R", 350),
//            arrayOf("SMOOTHIES", "Peach", "L", 400),
//            // Chats
//            arrayOf("Chats", "Gol-Gappy", "R", 200),
//            arrayOf("Chats", "Gol-Gappy", "L", 350),
//            arrayOf("Chats", "Russian", "R", 300),
//            arrayOf("Chats", "Russian", "L", 400),
//            arrayOf("Chats", "Dahi", "L", 250),
//            arrayOf("Chats", "Chana", "L", 250),
//            arrayOf("Chats", "Fruit", "L", 350),
//            arrayOf("Chats", "Flooda", "L", 350),
//            // Thai and Sweet
//            arrayOf("Thai and Sweet", "Tea", "-", 120),
//            arrayOf("Thai and Sweet", "Hot Coffee", "-", 250),
//            arrayOf("Thai and Sweet", "Kashmiri Tea", "-", 200),
//            arrayOf("Thai and Sweet", "Black Coffee", "-", 250),
//            arrayOf("Thai and Sweet", "Cappuccino", "-", 250),
//            arrayOf("Thai and Sweet", "Cold Coffee", "-", 500),
//            // ✅ Continue with Fresh Juices, Fresh Limonade, Mix Fruit Juices, Veg Juice & Detox...
//        )
//
//        connection.use { conn ->
//            // Insert only if table is empty
//            val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM menu_items")
//            if (rs.next() && rs.getInt(1) == 0) {
//                val sql = "INSERT INTO menu_items (category, name, size, price) VALUES (?, ?, ?, ?)"
//                conn.prepareStatement(sql).use { pstmt ->
//                    for (item in menuItems) {
//                        pstmt.setString(1, item[0] as String)
//                        pstmt.setString(2, item[1] as String)
//                        pstmt.setString(3, item[2] as String)
//                        pstmt.setDouble(4, (item[3] as Number).toDouble())
//                        pstmt.addBatch()
//                    }
//                    pstmt.executeBatch()
//                }
//            }
//        }
//    }
//
//    val connection: Connection
//        get() = DriverManager.getConnection(DB_URL)
//}