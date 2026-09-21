package com.innovatewithomer.juiceforu

import com.innovatewithomer.juiceforu.controller.recipeMenuItemLabel
import com.innovatewithomer.juiceforu.models.MenuItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecipeMenuItemLabelTest {
    @Test
    fun `recipe choices distinguish size and category variants`() {
        val regular = MenuItem(id = 1, category = "Shakes", name = "Banana Shake", size = "R", price = 250.0)
        val large = regular.copy(id = 2, size = "L", price = 350.0)

        assertEquals("Banana Shake (R) · Shakes", recipeMenuItemLabel(regular))
        assertEquals("Banana Shake (L) · Shakes", recipeMenuItemLabel(large))
    }
}
