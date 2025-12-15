package com.innovatewithomer.juiceforu.models

data class Order(
    val id: Int = 0,
    val orderNo: Int =0,
    var total: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<OrderItem> = emptyList(),
    val orderType: String,   // ✅ new
    var orderStatus: String? = "PENDING",
    val deliveryCharges: Int = 0,
    val serviceCharges: Int = 0,
    var isEdited: Boolean = false,
    // 🔹 New fields
    val customerPhone: String? = null,
    val customerAddress: String? = null,
    var discountPercent: Double = 0.0,
    var discountAmount: Double = 0.0

)


data class OrderItem(
    val id: Int = 0,
    val orderId: Int,
    val menuItemId: Int,   // ✅ keep link to menu
    val itemName: String,
    val category: String,
    val size: String,
    val price: Double,
    val quantity: Int
)


