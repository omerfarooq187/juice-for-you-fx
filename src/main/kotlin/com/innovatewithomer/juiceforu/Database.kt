package com.innovatewithomer.juiceforu

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.SQLException
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

object Database {
    private var dbUrl: String = "jdbc:sqlite:${AppPaths.databasePath}"
    @Volatile
    private var dataSource: HikariDataSource? = null
    private val lock = Any()

    /**
     * Allows configuring a custom DB URL (e.g. for testing environments).
     */
    fun configure(url: String) {
        synchronized(lock) {
            if (dbUrl != url) {
                close()
                dbUrl = url
            }
        }
    }

    fun configureDefaultLocation() {
        val path = AppPaths.prepareDatabase()
        configure("jdbc:sqlite:${path.toAbsolutePath()}")
    }

    fun currentPath(): Path? = dbUrl.removePrefix("jdbc:sqlite:")
        .takeIf { it != dbUrl && it.isNotBlank() }
        ?.let { Path.of(it).toAbsolutePath().normalize() }

    /**
     * Initializes the connection pool if not already initialized.
     */
    private fun getDataSource(): DataSource {
        val existing = dataSource
        if (existing != null && !existing.isClosed) {
            return existing
        }
        return synchronized(lock) {
            val doubleCheck = dataSource
            if (doubleCheck != null && !doubleCheck.isClosed) {
                doubleCheck
            } else {
                val config = HikariConfig().apply {
                    jdbcUrl = dbUrl
                    driverClassName = "org.sqlite.JDBC"
                    // SQLite has one writer; a small pool supports concurrent reads
                    // without creating avoidable write contention.
                    maximumPoolSize = 4
                    minimumIdle = 1
                    idleTimeout = 30000
                    connectionTimeout = 10000
                    maxLifetime = 600000
                    poolName = "JuiceForU-HikariPool"
                    connectionInitSql = "PRAGMA journal_mode = WAL"
                    addDataSourceProperty("cachePrepStmts", "true")
                    addDataSourceProperty("prepStmtCacheSize", "250")
                    addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
                }
                HikariDataSource(config).also { dataSource = it }
            }
        }
    }

    /**
     * Retrieves a pooled database connection.
     */
    fun getConnection(): Connection {
        val connection = getDataSource().connection
        try {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA busy_timeout = 5000")
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("PRAGMA synchronous = NORMAL")
            }
            return connection
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }

    /**
     * Executes a block within an atomic transaction.
     * Automatically commits upon successful completion or rolls back if an exception occurs.
     */
    fun <T> transaction(block: (Connection) -> T): T {
        getConnection().use { conn ->
            val initialAutoCommit = conn.autoCommit
            try {
                conn.autoCommit = false
                val result = block(conn)
                conn.commit()
                return result
            } catch (e: Exception) {
                try {
                    conn.rollback()
                } catch (rollbackEx: SQLException) {
                    e.addSuppressed(rollbackEx)
                }
                throw e
            } finally {
                try {
                    conn.autoCommit = initialAutoCommit
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Safe database initialization.
     * Creates all required tables and indexes if they do not exist.
     * 100% backward compatible with existing database files.
     */
    fun init() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                // 1. Customers Table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS customers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        phone TEXT,
                        email TEXT,
                        type TEXT DEFAULT 'Regular'
                    )
                    """.trimIndent()
                )

                // 2. Orders Table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS orders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        order_no INTEGER DEFAULT 0,
                        total INTEGER NOT NULL,
                        order_type TEXT NOT NULL DEFAULT 'Takeaway',
                        order_status TEXT DEFAULT 'PENDING',
                        delivery_charges REAL DEFAULT 0,
                        service_charges REAL DEFAULT 0,
                        discount_percent REAL DEFAULT 0,
                        discount_amount REAL DEFAULT 0,
                        created_at BIGINT,
                        is_edited INTEGER DEFAULT 0,
                        inventory_usage_recorded INTEGER NOT NULL DEFAULT 0,
                        address TEXT,
                        phone TEXT
                    )
                    """.trimIndent()
                )

                // 3. Menu Items Table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS menu_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        category TEXT NOT NULL,
                        name TEXT NOT NULL,
                        size TEXT NOT NULL,
                        price REAL NOT NULL
                    )
                    """.trimIndent()
                )

                // 4. Order Items Table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS order_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        order_id INTEGER NOT NULL,
                        menu_item_id INTEGER,
                        item_name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        size TEXT NOT NULL,
                        price REAL NOT NULL,
                        quantity INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE,
                        FOREIGN KEY(menu_item_id) REFERENCES menu_items(id)
                    )
                    """.trimIndent()
                )

                // 5. Inventory Items Table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        reorder_level REAL NOT NULL
                    )
                    """.trimIndent()
                )

                // 6. Recipes Table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS recipes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        menu_item_id INTEGER NOT NULL,
                        ingredient_id INTEGER NOT NULL,
                        quantity_needed REAL NOT NULL,
                        fulfillment_scope TEXT NOT NULL DEFAULT 'ALL',
                        FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE,
                        FOREIGN KEY (ingredient_id) REFERENCES inventory_items(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 7. Order Number Tracker Table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS order_number_tracker (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        last_reset_date TEXT,
                        last_order_no INTEGER
                    )
                    """.trimIndent()
                )

                // Existing installations gain the new columns without rewriting their data.
                if (!hasColumn(conn, "recipes", "fulfillment_scope")) {
                    stmt.executeUpdate("ALTER TABLE recipes ADD COLUMN fulfillment_scope TEXT NOT NULL DEFAULT 'ALL'")
                }
                if (!hasColumn(conn, "orders", "inventory_usage_recorded")) {
                    stmt.executeUpdate("ALTER TABLE orders ADD COLUMN inventory_usage_recorded INTEGER NOT NULL DEFAULT 0")
                }
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS order_inventory_usage (
                        order_id INTEGER NOT NULL,
                        ingredient_id INTEGER NOT NULL,
                        quantity REAL NOT NULL,
                        PRIMARY KEY (order_id, ingredient_id),
                        FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 8. Performance Indexes (Non-destructive)
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);")
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(order_status);")
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);")
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_recipes_menu_item_id ON recipes(menu_item_id);")
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_menu_items_category ON menu_items(category);")
            }
        }
    }

    private fun hasColumn(conn: Connection, table: String, column: String): Boolean =
        conn.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                while (rows.next()) if (rows.getString("name") == column) return true
                false
            }
        }

    /** Creates a transactionally consistent standalone SQLite backup. */
    fun backupTo(destination: Path) {
        val normalized = destination.toAbsolutePath().normalize()
        require(!Files.exists(normalized)) { "Backup destination already exists: $normalized" }
        Files.createDirectories(normalized.parent)
        getConnection().use { conn ->
            conn.createStatement().use { it.execute("PRAGMA wal_checkpoint(PASSIVE)") }
            conn.prepareStatement("VACUUM INTO ?").use { statement ->
                statement.setString(1, normalized.toString())
                statement.execute()
            }
        }
        check(Files.isRegularFile(normalized) && Files.size(normalized) > 0) {
            "SQLite did not create a valid backup at $normalized"
        }
    }

    fun integrityCheck(): String = getConnection().use { conn ->
        conn.createStatement().use { statement ->
            statement.executeQuery("PRAGMA integrity_check").use { result ->
                if (result.next()) result.getString(1) else "No result"
            }
        }
    }

    /**
     * Closes the connection pool.
     */
    fun close() {
        synchronized(lock) {
            dataSource?.let {
                if (!it.isClosed) {
                    it.close()
                }
            }
            dataSource = null
        }
    }
}
