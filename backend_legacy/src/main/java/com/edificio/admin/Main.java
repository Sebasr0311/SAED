package com.edificio.admin;

import com.edificio.admin.rest.RestServer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        try {
            // Iniciar servidor REST en puerto 8080 (hilo separado, no bloquea JavaFX)
            RestServer.start(8080);

            Parent root = FXMLLoader.load(getClass().getResource("/com/edificio/admin/view/vistas/Login.fxml"));

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/com/edificio/admin/view/estilos/estilos.css").toExternalForm());

            stage.setTitle("Login - Sistema de Administracion Residencial");
            stage.setWidth(800);
            stage.setHeight(600);
            stage.setResizable(true);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error de inicio");
            alert.setHeaderText("No se pudo iniciar la aplicacion");
            alert.setContentText(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            alert.showAndWait();
        }
    }

    @Override
    public void stop() {
        RestServer.stop();
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}