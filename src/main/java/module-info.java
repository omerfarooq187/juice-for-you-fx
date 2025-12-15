module com.innovatewithomer.juiceforu {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;
    requires java.desktop;
    requires kotlin.stdlib;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires escpos.coffee;

    requires atlantafx.base;

    opens com.innovatewithomer.juiceforu to javafx.fxml;
    opens com.innovatewithomer.juiceforu.controller to javafx.fxml;
    opens com.innovatewithomer.juiceforu.models to javafx.base;

    exports com.innovatewithomer.juiceforu;
    exports com.innovatewithomer.juiceforu.controller;
    exports com.innovatewithomer.juiceforu.models;
}