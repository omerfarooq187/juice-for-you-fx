package com.innovatewithomer.juiceforu

import atlantafx.base.theme.PrimerLight
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import com.innovatewithomer.juiceforu.utils.ResponsiveScale
import com.innovatewithomer.juiceforu.utils.Logger

class JuiceForYouApp : Application() {
    override fun init() {
        super.init()
        Database.configureDefaultLocation()
        Database.init()
        try {
            BackupService.createAutomaticBackupIfDue()
        } catch (e: Exception) {
            Logger.logError(e, "Automatic backup failed during startup")
        }
    }

    override fun start(primaryStage: Stage) {
        // Load Atlantafx theme
        setUserAgentStylesheet(PrimerLight().userAgentStylesheet)

        // --- Load main view ---
        val fxmlPath = "/com/innovatewithomer/juiceforu/main-view.fxml"
        val loader = FXMLLoader(javaClass.getResource(fxmlPath))
        val root: Parent = loader.load()

        // --- Create responsive scene sized to the actual screen ---
        val (sceneWidth, sceneHeight) = ResponsiveScale.sceneDimensions()
        val scene = Scene(root, sceneWidth, sceneHeight)

        javaClass.getResource("/com/innovatewithomer/juiceforu/styles/style.css")?.let {
            scene.stylesheets.add(it.toExternalForm())
        }

        // Apply DPI-aware base font size so all em-based CSS values scale
        ResponsiveScale.applyTo(scene)

        primaryStage.icons.add(BrandAssets.logo)

        // --- Setup stage ---
        primaryStage.title = AppBrand.current.windowTitle
        primaryStage.scene = scene
        primaryStage.isResizable = true
        primaryStage.centerOnScreen()

        // Ensure min size so the app is never unusably small
        primaryStage.minWidth = 800.0
        primaryStage.minHeight = 500.0

        primaryStage.show()

        if (System.getProperty("juiceforu.smokeTest") == "true") {
            loader.getController<MainMenuController>().smokeTestViews()
            println("UI smoke test loaded every application screen successfully.")
            Platform.exit()
        }
    }

    override fun stop() {
        Database.close()
        super.stop()
    }
}

fun main() {
    Application.launch(JuiceForYouApp::class.java)
}
