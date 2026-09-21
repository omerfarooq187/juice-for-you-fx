package com.innovatewithomer.juiceforu

import com.innovatewithomer.juiceforu.models.InventoryItem
import com.innovatewithomer.juiceforu.models.MenuItem
import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.models.OrderItem
import com.innovatewithomer.juiceforu.models.RecipeItem
import com.innovatewithomer.juiceforu.models.RecipeUsage
import com.innovatewithomer.juiceforu.repo.InventoryRepository
import com.innovatewithomer.juiceforu.repo.MenuItemRepository
import com.innovatewithomer.juiceforu.repo.OrderRepository
import com.innovatewithomer.juiceforu.repo.RecipeRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecipeInventoryTest {
    private lateinit var databaseFile: Path

    @BeforeAll
    fun setup() {
        databaseFile = Files.createTempFile("juiceforu-recipes-", ".db")
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(
                    """CREATE TABLE orders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT, order_no INTEGER DEFAULT 0, total INTEGER NOT NULL,
                        order_type TEXT NOT NULL DEFAULT 'Takeaway', order_status TEXT DEFAULT 'PENDING',
                        delivery_charges REAL DEFAULT 0, service_charges REAL DEFAULT 0,
                        discount_percent REAL DEFAULT 0, discount_amount REAL DEFAULT 0,
                        created_at BIGINT, is_edited INTEGER DEFAULT 0, address TEXT, phone TEXT
                    )""".trimIndent()
                )
                stmt.executeUpdate("INSERT INTO orders (order_no, total, order_type, created_at) VALUES (99, 0, 'Service', 0)")
                stmt.executeUpdate("CREATE TABLE recipes (id INTEGER PRIMARY KEY AUTOINCREMENT, menu_item_id INTEGER NOT NULL, ingredient_id INTEGER NOT NULL, quantity_needed REAL NOT NULL)")
                stmt.executeUpdate("INSERT INTO recipes (menu_item_id, ingredient_id, quantity_needed) VALUES (999, 999, 0.25)")
            }
        }
        Database.configure("jdbc:sqlite:${databaseFile.toAbsolutePath()}")
        Database.init()
    }

    @AfterAll
    fun teardown() {
        Database.close()
        Files.deleteIfExists(databaseFile)
    }

    @Test
    fun `legacy recipes remain valid and default to all order types`() {
        val recipe = RecipeRepository().getRecipesForMenuItem(999).single()
        assertEquals(RecipeUsage.ALL, recipe.usage)
        assertEquals(0.25, recipe.quantityNeeded)
        Database.getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT inventory_usage_recorded FROM orders WHERE order_no = 99").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `packaging applies only to takeaway and delivery and edits restore saved usage`() = runBlocking {
        val inventory = InventoryRepository()
        val recipes = RecipeRepository()
        val orders = OrderRepository()
        inventory.addItem(InventoryItem(name = "Test fruit", unit = "kg", quantity = 6.28, reorderLevel = 1.0))
        inventory.addItem(InventoryItem(name = "Test box", unit = "pcs", quantity = 10.0, reorderLevel = 1.0))
        val fruitId = inventory.getAllItems().single { it.name == "Test fruit" }.id
        val boxId = inventory.getAllItems().single { it.name == "Test box" }.id
        MenuItemRepository.addMenuItem(MenuItem(category = "Test", name = "Test drink", size = "Regular", price = 100.0))
        val menuId = MenuItemRepository.getAllMenuItems().single { it.name == "Test drink" }.id!!
        recipes.addRecipe(RecipeItem(menuItemId = menuId, ingredientId = fruitId, quantityNeeded = 0.25))
        recipes.addRecipe(RecipeItem(menuItemId = menuId, ingredientId = boxId, quantityNeeded = 1.0,
            usage = RecipeUsage.TAKEAWAY_DELIVERY))
        assertEquals(RecipeUsage.TAKEAWAY_DELIVERY, recipes.getRecipesForMenuItem(menuId).single { it.ingredientId == boxId }.usage)

        val serviceUsage = recipes.calculateUsage(listOf(menuId to 1), "Service")
        val takeawayUsage = recipes.calculateUsage(listOf(menuId to 1), "Takeaway")
        assertEquals(listOf(fruitId to 0.25), serviceUsage)
        assertEquals(serviceUsage, recipes.calculateUsage(listOf(menuId to 1), "Takeaway", includeConditionalRecipes = false))
        assertEquals(listOf(fruitId to 0.25, boxId to 1.0).sortedBy { it.first }, takeawayUsage)
        assertEquals(takeawayUsage, recipes.calculateUsage(listOf(menuId to 1), "Delivery"))

        val item = OrderItem(orderId = 0, menuItemId = menuId, itemName = "Test drink", category = "Test",
            size = "Regular", price = 100.0, quantity = 1)
        val saved = orders.saveOrderAtomic(Order(orderNo = 301, total = 100, orderType = "Service"), listOf(item), serviceUsage)
        assertEquals(6.03, inventory.getItemById(fruitId)!!.quantity, 0.00001)
        assertEquals(10.0, inventory.getItemById(boxId)!!.quantity, 0.00001)

        val fruitRecipe = recipes.getRecipesForMenuItem(menuId).single { it.ingredientId == fruitId }
        recipes.updateRecipe(fruitRecipe.copy(quantityNeeded = 0.50))
        val newTakeawayUsage = recipes.calculateUsage(listOf(menuId to 1), "Takeaway")
        val takeaway = orders.updateOrderAtomic(saved.copy(orderType = "Takeaway"), listOf(item.copy(orderId = saved.id)),
            listOf(fruitId to 9.0), newTakeawayUsage)
        assertEquals(5.78, inventory.getItemById(fruitId)!!.quantity, 0.00001)
        assertEquals(9.0, inventory.getItemById(boxId)!!.quantity, 0.00001)

        val updatedServiceUsage = recipes.calculateUsage(listOf(menuId to 1), "Service")
        orders.updateOrderAtomic(takeaway.copy(orderType = "Service"), listOf(item.copy(orderId = saved.id)),
            emptyList(), updatedServiceUsage)
        assertEquals(5.78, inventory.getItemById(fruitId)!!.quantity, 0.00001)
        assertEquals(10.0, inventory.getItemById(boxId)!!.quantity, 0.00001)
        assertTrue(orders.getOrderById(saved.id) != null)
    }
}
