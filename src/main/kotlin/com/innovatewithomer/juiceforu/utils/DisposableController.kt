package com.innovatewithomer.juiceforu.utils

interface DisposableController {
    fun disposeSafely() {
        try {
            dispose()
        } catch (e: Exception) {
            println("⚠️ Error disposing controller: ${e.message}")
        }
    }
    fun dispose()
}
