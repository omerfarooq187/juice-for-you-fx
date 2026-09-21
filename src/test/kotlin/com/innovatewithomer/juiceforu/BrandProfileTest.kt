package com.innovatewithomer.juiceforu

import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.utils.ReceiptPrinter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class BrandProfileTest {
    @Test
    fun `pizza shop profile has independent identity and database`() {
        val profile = AppBrand.profileFor(AppBrand.MANDRA_PIZZA_HUT)

        assertEquals("Mandra Pizza Hut", profile.businessName)
        assertEquals("pizza_hut.db", profile.databaseFileName)
        assertEquals("0309-5107040", profile.whatsapp)
        assertEquals("Mandra Pizza Hut", profile.dataDirectoryName)
        assertNotNull(javaClass.getResource(profile.logoResource))
        assertNotNull(javaClass.getResource(profile.receiptLogoResource))
    }

    @Test
    fun `unknown brand safely uses default shop`() {
        assertEquals(AppBrand.JUICE_FOR_U, AppBrand.profileFor("unknown").id)
    }

    @Test
    fun `pizza receipt uses shop contact details and footer`() {
        val profile = AppBrand.profileFor(AppBrand.MANDRA_PIZZA_HUT)
        val receipt = ReceiptPrinter(profile).buildReceiptContent(
            Order(orderNo = 219, total = 700, orderType = "Delivery")
        ).toString(Charset.forName("CP437"))

        assertTrue(receipt.contains("MANDRA PIZZA HUT"))
        assertTrue(receipt.contains("WhatsApp: 0309-5107040"))
        assertTrue(receipt.contains("YOUR OPINION HELPS US IMPROVE"))
        assertTrue(receipt.contains("Developed by InnovateWithOmer"))
    }
}
