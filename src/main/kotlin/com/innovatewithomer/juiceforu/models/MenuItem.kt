package com.innovatewithomer.juiceforu.models

data class MenuItem(
    val id: Int? = 0,
    val category: String,
    val name: String,
    val size: String,
    val price: Double
)
