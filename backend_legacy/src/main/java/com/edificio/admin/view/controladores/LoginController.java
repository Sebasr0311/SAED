package com.edificio.admin.view.controladores;

import com.edificio.admin.config.SesionUsuario;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.model.Usuario;
import com.edificio.admin.model.enums.TipoRol;
import com.edificio.admin.service.UsuarioService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {
    @FXML
    private TextField txtUsuario;
    @FXML
    private PasswordField txtContrasena;

    private final UsuarioService usuarioService = new UsuarioService();

    @FXML
    private void initialize() {
    }

    @FXML
    private void iniciarSesion() {
        try {
            String usuario = txtUsuario.getText();
            String contrasena = txtContrasena.getText();

            Usuario user = usuarioService.autenticar(usuario, contrasena);

            SesionUsuario.setUsuarioActual(user);
            mostrarAlerta(Alert.AlertType.INFORMATION, "Bienvenido",
                "Bienvenido, " + user.getUsername() + " [" + user.getRol().name() + "]");
            cargarDashboard(user);
        } catch (DatosInvalidosException e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validación", e.getMessage());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "Error al iniciar sesión: " + e.getMessage());
        }
    }

    private void cargarDashboard(Usuario usuario) {
        try {
            Stage stage = (Stage) txtUsuario.getScene().getWindow();

            if (usuario.getRol() == TipoRol.RESIDENTE) {
                // Panel exclusivo para residentes
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/edificio/admin/view/vistas/ResidenteDashboard.fxml"));
                Parent root = loader.load();
                ResidenteDashboardController controller = loader.getController();
                controller.inicializar(usuario);
                stage.setScene(new Scene(root));
                stage.setTitle("Mi Portal - Sistema de Administración Residencial");
            } else {
                // ADMIN y PORTERO usan el dashboard general
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/edificio/admin/view/vistas/Dashboard.fxml"));
                Parent root = loader.load();
                DashboardController controller = loader.getController();
                controller.inicializar(usuario);
                stage.setScene(new Scene(root));
                stage.setTitle("Sistema de Administración Residencial");
            }

            stage.show();
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "Error al cargar el dashboard: " + e.getMessage());
        }
    }

    @FXML
    private void recuperarContrasena() {
        mostrarAlerta(Alert.AlertType.INFORMATION, "Recuperar Contraseña",
            "Contacte al administrador del sistema");
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}