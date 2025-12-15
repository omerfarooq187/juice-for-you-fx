package com.innovatewithomer.juiceforu

import atlantafx.base.theme.PrimerLight
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Screen
import javafx.stage.Stage

class JuiceForYouApp : Application() {
    override fun start(primaryStage: Stage) {
        // Load Atlantafx theme
        setUserAgentStylesheet(PrimerLight().userAgentStylesheet)

        // --- Detect screen resolution and scale ---
        val screen = Screen.getPrimary()
        val bounds = screen.bounds
        val visual = screen.visualBounds
        val scaleX = screen.outputScaleX
        val scaleY = screen.outputScaleY

        println("Detected Screen Resolution: ${bounds.width}x${bounds.height}")
        println("Visual Bounds: ${visual.width}x${visual.height}")
        println("ScaleX=$scaleX, ScaleY=$scaleY")

        // --- Smart DPI handling ---
        // If scaling is large (like 1.25–1.75), disable HiDPI for accuracy
        if (scaleX > 1.3 || scaleY > 1.3) {
            System.setProperty("prism.allowhidpi", "false")
            println("HiDPI scaling detected ($scaleX×). Disabled for true 1:1 rendering.")
        } else {
            System.setProperty("prism.allowhidpi", "true")
        }

        // --- Load main view ---
        val fxmlPath = "/com/innovatewithomer/juiceforu/main-view.fxml"
        val root: Parent = FXMLLoader.load(javaClass.getResource(fxmlPath))

        // --- Create responsive scene ---
        val sceneWidth = if (visual.width < 1366) 960.0 else 1100.0
        val sceneHeight = if (visual.height < 768) 580.0 else 680.0

        val scene = Scene(root, sceneWidth, sceneHeight)
        javaClass.getResource("/com/innovatewithomer/juiceforu/styles/style.css")?.let {
            scene.stylesheets.add(it.toExternalForm())
        }

        // --- Load icon ---
        val iconPaths = listOf(
            "/com/innovatewithomer/juiceforu/logo/logo.jpg",
            "/com/innovatewithomer/juiceforu/logo_launcher/logo.ico"
        )

        var iconLoaded = false
        for (path in iconPaths) {
            val url = javaClass.getResource(path)
            if (url != null) {
                primaryStage.icons.add(Image(url.toExternalForm()))
                iconLoaded = true
                break
            }
        }
        if (!iconLoaded) {
            println("⚠️ Icon not found in resources.")
        }

        // --- Setup stage ---
        primaryStage.title = "Juice For U - POS"
        primaryStage.scene = scene
        primaryStage.isResizable = true
        primaryStage.centerOnScreen()
        primaryStage.show()

        // --- Auto fit window to visual bounds if too small ---
        if (scene.width > visual.width || scene.height > visual.height) {
            primaryStage.width = visual.width - 50
            primaryStage.height = visual.height - 50
            println("Adjusted to fit smaller screen.")
        }
    }
}

fun main() {
    Application.launch(JuiceForYouApp::class.java)
}
