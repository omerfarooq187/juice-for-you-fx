package com.innovatewithomer.juiceforu

import com.innovatewithomer.juiceforu.repo.DashboardRepository
import com.innovatewithomer.juiceforu.repo.OrderRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.LocalTime

class BusinessDayReportTest {
    @Test
    fun `dashboard and order history use the same cutoff`() = runBlocking {
        val originalCutoff = AppSettings.businessDayStart
        val testDb = Files.createTempFile("juiceforu-business-day-", ".db")
        try {
            AppSettings.businessDayStart = LocalTime.of(1, 0)
            Database.configure("jdbc:sqlite:${testDb.toAbsolutePath()}")
            Database.init()
            val start = BusinessDay.today().startInclusive
            Database.getConnection().use { conn ->
                conn.prepareStatement("INSERT INTO orders (order_no, total, order_type, created_at) VALUES (?, ?, 'Takeaway', ?)").use { stmt ->
                    stmt.setInt(1, 301)
                    stmt.setInt(2, 100)
                    stmt.setLong(3, start - 1)
                    stmt.executeUpdate()
                    stmt.setInt(1, 302)
                    stmt.setInt(2, 250)
                    stmt.setLong(3, start)
                    stmt.executeUpdate()
                }
            }

            assertEquals(250, DashboardRepository.getTodaySales())
            assertEquals(1, DashboardRepository.getTodayOrders())
            assertEquals(1, DashboardRepository.getRecentOrders().size)
            val repo = OrderRepository()
            assertEquals(listOf(302), repo.getOrdersByDate("today").map { it.orderNo })
            assertEquals(250.0, repo.getSalesSummary()["today"])
            Database.getConnection().use { conn ->
                conn.prepareStatement("INSERT INTO order_number_tracker (last_reset_date, last_order_no) VALUES (?, 245)").use { stmt ->
                    stmt.setString(1, BusinessDay.currentDate().minusDays(1).toString())
                    stmt.executeUpdate()
                }
            }
            assertEquals(201, repo.peekNextOrderNo())
            assertEquals(201, repo.getNextOrderNo())
            assertEquals(202, repo.peekNextOrderNo())
        } finally {
            Database.close()
            AppSettings.businessDayStart = originalCutoff
            Files.deleteIfExists(testDb)
        }
    }
}
