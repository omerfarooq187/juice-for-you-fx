package com.innovatewithomer.juiceforu.controller

import javafx.fxml.FXML
import javafx.scene.control.ChoiceBox
import javafx.scene.control.TextField

class SettingsController {

    @FXML private lateinit var themeChoice: ChoiceBox<String>
    @FXML private lateinit var companyNameField: TextField
    @FXML private lateinit var emailField: TextField

    @FXML
    fun initialize() {
        themeChoice.items.addAll("Light", "Dark", "Custom")
        themeChoice.selectionModel.selectFirst()
    }

    @FXML
    private fun onSaveSettingsClick() {
        val theme = themeChoice.value
        val company = companyNameField.text
        val email = emailField.text

        println("✅ Settings saved -> Theme: $theme, Company: $company, Email: $email")
        // TODO: Save to DB or config file
    }
}
