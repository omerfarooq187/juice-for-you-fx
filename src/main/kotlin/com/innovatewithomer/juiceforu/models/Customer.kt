package com.innovatewithomer.juiceforu.models

data class Customer(
    val id: Int = 0,
    val name: String,
    val phone: String?,
    val email: String?,
    val type: String = "Regular"
) {
    override fun toString(): String = "$name ($type)"
}
