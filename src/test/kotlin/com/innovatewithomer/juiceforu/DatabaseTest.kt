package com.innovatewithomer.juiceforu

import com.innovatewithomer.juiceforu.models.Customer
import com.innovatewithomer.juiceforu.models.InventoryItem
import com.innovatewithomer.juiceforu.models.MenuItem
import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.models.OrderItem
import com.innovatewithomer.juiceforu.models.RecipeItem
import com.innovatewithomer.juiceforu.repo.CustomerRepository
import com.innovatewithomer.juiceforu.repo.InventoryRepository
import com.innovatewithomer.juiceforu.repo.MenuItemRepository
import com.innovatewithomer.juiceforu.repo.OrderRepository
import com.innovatewithomer.juiceforu.repo.RecipeRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseTest {

    private val testDbFile = File("target/test_juiceforyou.db")
    private val orderRepo = OrderRepository()
    private val inventoryRepo = InventoryRepository()
    private val recipeRepo = RecipeRepository()
    private val customerRepo = CustomerRepository()

    @BeforeAll
    fun setup() {
        if (testDbFile.exists()) {
            testDbFile.delete()
        }
        Database.configure("jdbc:sqlite:${testDbFile.absolutePath}")
        Database.init()
    }

    @AfterAll
    fun teardown() {
        Database.close()
        if (testDbFile.exists()) {
            testDbFile.delete()
        }
    }

    @Test
    fun testDatabaseInitializationAndSchema() {
        Database.getConnection().use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery("PRAGMA foreign_keys").use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt(1), "foreign-key enforcement should be enabled")
                }
            }
            val tables = mutableListOf<String>()
            conn.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"))
                }
            }
            assertTrue(tables.contains("customers"), "customers table should exist")
            assertTrue(tables.contains("orders"), "orders table should exist")
            assertTrue(tables.contains("order_items"), "order_items table should exist")
            assertTrue(tables.contains("menu_items"), "menu_items table should exist")
            assertTrue(tables.contains("inventory_items"), "inventory_items table should exist")
            assertTrue(tables.contains("recipes"), "recipes table should exist")
            assertTrue(tables.contains("order_number_tracker"), "order_number_tracker table should exist")
        }
    }

    @Test
    fun testMenuItemCrud() {
        val item = MenuItem(
            id = 0,
            category = "Juices",
            name = "Fresh Orange",
            size = "Large",
            price = 350.0
        )
        MenuItemRepository.addMenuItem(item)
        val all = MenuItemRepository.getAllMenuItems()
        val found = all.find { it.name == "Fresh Orange" && it.size == "Large" }
        assertNotNull(found)
        assertEquals(350.0, found!!.price)

        val updated = found.copy(price = 380.0)
        MenuItemRepository.updateMenuItem(updated)
        val afterUpdate = MenuItemRepository.getAllMenuItems().find { it.id == found.id }
        assertNotNull(afterUpdate)
        assertEquals(380.0, afterUpdate!!.price)
    }

    @Test
    fun testInventoryAndRecipeOperations() {
        val inv = InventoryItem(
            id = 0,
            name = "Fresh Mangoes",
            unit = "kg",
            quantity = 50.0,
            reorderLevel = 10.0
        )
        inventoryRepo.addItem(inv)
        val items = inventoryRepo.getAllItems()
        val savedInv = items.find { it.name == "Fresh Mangoes" }
        assertNotNull(savedInv)
        assertEquals(50.0, savedInv!!.quantity)

        inventoryRepo.adjustStock(savedInv.id, -5.0)
        val adjusted = inventoryRepo.getItemById(savedInv.id)
        assertNotNull(adjusted)
        assertEquals(45.0, adjusted!!.quantity)
    }

    @Test
    fun testCustomerRepository() {
        val customer = Customer(
            id = 0,
            name = "Test Customer",
            phone = "03001234567",
            email = "test@example.com",
            type = "VIP"
        )
        customerRepo.addCustomer(customer)
        val customers = customerRepo.getAllCustomers()
        val found = customers.find { it.phone == "03001234567" }
        assertNotNull(found)
        assertEquals("Test Customer", found!!.name)
        assertEquals("VIP", found.type)
    }

    @Test
    fun testAtomicOrderSaveAndInventoryDeduction() = runBlocking {
        // 1. Create ingredient
        val ingredient = InventoryItem(
            id = 0,
            name = "Strawberries",
            unit = "kg",
            quantity = 20.0,
            reorderLevel = 5.0
        )
        inventoryRepo.addItem(ingredient)
        val savedIngredient = inventoryRepo.getAllItems().find { it.name == "Strawberries" }!!

        // 2. Create menu item
        val menuItem = MenuItem(
            id = 0,
            category = "Smoothies",
            name = "Strawberry Blast",
            size = "Regular",
            price = 400.0
        )
        MenuItemRepository.addMenuItem(menuItem)
        val savedMenuItem = MenuItemRepository.getAllMenuItems().find { it.name == "Strawberry Blast" }!!

        // 3. Create recipe: 0.5 kg strawberries per smoothie
        val recipe = RecipeItem(
            id = 0,
            menuItemId = savedMenuItem.id ?: 0,
            ingredientId = savedIngredient.id,
            quantityNeeded = 0.5
        )
        recipeRepo.addRecipe(recipe)

        // 4. Place atomic order for 2 smoothies (requires 1.0 kg strawberries)
        val orderNo = orderRepo.getNextOrderNo()
        val order = Order(
            id = 0,
            orderNo = orderNo,
            total = 800,
            orderType = "Takeaway",
            createdAt = System.currentTimeMillis()
        )
        val orderItem = OrderItem(
            id = 0,
            orderId = 0,
            menuItemId = savedMenuItem.id ?: 0,
            itemName = savedMenuItem.name,
            category = savedMenuItem.category,
            size = savedMenuItem.size,
            price = savedMenuItem.price,
            quantity = 2
        )
        val deductions = listOf(savedIngredient.id to (0.5 * 2))

        val savedOrder = orderRepo.saveOrderAtomic(order, listOf(orderItem), deductions)
        assertNotNull(savedOrder)
        assertTrue(savedOrder.id > 0)
        assertEquals(800, savedOrder.total)
        assertEquals(1, savedOrder.items.size)
        assertEquals("Strawberry Blast", savedOrder.items[0].itemName)

        // Verify inventory decreased from 20.0 to 19.0
        val afterStock = inventoryRepo.getItemById(savedIngredient.id)!!
        assertEquals(19.0, afterStock.quantity, 0.001)

        // 5. Update order atomically: change quantity from 2 to 3 (requires +0.5 kg additional deduction)
        val updatedOrder = savedOrder.copy(total = 1200)
        val updatedItem = orderItem.copy(orderId = savedOrder.id, quantity = 3)
        val restores = listOf(savedIngredient.id to 1.0) // restore old 1.0 kg
        val newDeductions = listOf(savedIngredient.id to 1.5) // deduct new 1.5 kg

        val resultUpdated = orderRepo.updateOrderAtomic(updatedOrder, listOf(updatedItem), restores, newDeductions)
        assertNotNull(resultUpdated)
        assertEquals(1200, resultUpdated.total)
        assertEquals(3, resultUpdated.items[0].quantity)

        // Verify inventory is now 18.5 (20 - 1.5)
        val finalStock = inventoryRepo.getItemById(savedIngredient.id)!!
        assertEquals(18.5, finalStock.quantity, 0.001)
    }

    @Test
    fun testTransactionRollbackOnError() {
        val initialStock = 100.0
        val ingredient = InventoryItem(
            id = 0,
            name = "Apple Test",
            unit = "kg",
            quantity = initialStock,
            reorderLevel = 5.0
        )
        inventoryRepo.addItem(ingredient)
        val savedIngredient = inventoryRepo.getAllItems().find { it.name == "Apple Test" }!!

        assertThrows(Exception::class.java) {
            Database.transaction { conn ->
                // Adjust stock
                inventoryRepo.adjustStock(savedIngredient.id, -20.0, conn)

                // Simulate unexpected failure
                throw RuntimeException("Simulated mid-transaction failure")
            }
        }

        // Verify stock was NOT modified due to rollback
        val stockAfterRollback = inventoryRepo.getItemById(savedIngredient.id)!!
        assertEquals(initialStock, stockAfterRollback.quantity, 0.001)
    }

    @Test
    fun testInsufficientStockStillSavesOrderAndCapsInventoryAtZero() = runBlocking {
        val ingredient = InventoryItem(0, "Limited Fruit", "kg", 0.5, 0.1)
        inventoryRepo.addItem(ingredient)
        val savedIngredient = inventoryRepo.getAllItems().first { it.name == "Limited Fruit" }
        val order = Order(
            id = 0,
            orderNo = orderRepo.getNextOrderNo(),
            total = 900,
            createdAt = System.currentTimeMillis(),
            items = emptyList(),
            orderType = "Takeaway",
            orderStatus = "PENDING"
        )
        val beforeOrders = orderRepo.getAllOrders().size

        val savedOrder = orderRepo.saveOrderAtomic(order, emptyList(), listOf(savedIngredient.id to 1.0))

        assertNotNull(savedOrder)
        assertEquals(beforeOrders + 1, orderRepo.getAllOrders().size)
        assertEquals(0.0, inventoryRepo.getItemById(savedIngredient.id)!!.quantity, 0.001)

        val missingIngredientOrder = order.copy(orderNo = orderRepo.getNextOrderNo())
        val savedWithMissingIngredient = orderRepo.saveOrderAtomic(
            missingIngredientOrder,
            emptyList(),
            listOf(999999 to 1.0)
        )
        assertNotNull(savedWithMissingIngredient)
        assertEquals(beforeOrders + 2, orderRepo.getAllOrders().size)
    }

    @Test
    fun testDatabaseIntegrityAndBackup() {
        assertEquals("ok", Database.integrityCheck())
        val backup = File("target/test_backup.db")
        if (backup.exists()) backup.delete()
        Database.backupTo(backup.toPath())
        assertTrue(backup.isFile && backup.length() > 0)
        backup.delete()
    }
}
