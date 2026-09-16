package com.innovatewithomer.juiceforu.utils

import javafx.scene.Scene
import javafx.stage.Screen

/**
 * Computes a DPI-aware base font size and applies it to the Scene root
 * so that every `em`-based CSS value scales automatically.
 *
 * Screens are bucketed by their **visual width** (after OS scaling).
 * The returned value is the CSS `-fx-font-size` applied to `.root`.
 */
object ResponsiveScale {

    /**
     * Calculate the ideal base font size for the primary screen.
     * This value is used as the root `-fx-font-size` so that all
     * relative sizes (`em`) scale accordingly.
     */
    fun baseFontSize(): Double {
        val visual = Screen.getPrimary().visualBounds
        val w = visual.width
        return when {
            w < 1024  -> 11.0   // very small / old netbook
            w < 1280  -> 12.0   // 1024×768, 1280×720
            w < 1440  -> 13.0   // 1366×768  — most common cheap laptop LCD
            w < 1680  -> 13.5   // 1440×900, 1600×900
            w < 1920  -> 14.0   // 1680×1050
            w < 2560  -> 14.0   // 1920×1080 Full HD
            else      -> 15.0   // 2K, 4K — high density
        }
    }

    /**
     * Apply the computed base font to the scene root so every child inherits it.
     */
    fun applyTo(scene: Scene) {
        val fontSize = baseFontSize()
        scene.root.style = "-fx-font-size: ${fontSize}px;"
    }

    /**
     * Recommended initial scene dimensions that leave a margin around
     * the screen edges so the OS taskbar is never covered.
     */
    fun sceneDimensions(): Pair<Double, Double> {
        val visual = Screen.getPrimary().visualBounds
        // Use 95% of the visual area (respects taskbar)
        val w = (visual.width * 0.95).coerceAtMost(visual.width - 40.0)
        val h = (visual.height * 0.95).coerceAtMost(visual.height - 40.0)
        return w to h
    }
}
