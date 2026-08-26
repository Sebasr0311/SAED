package com.edificio.admin.view.controladores;

import com.edificio.admin.config.SesionUsuario;
import com.edificio.admin.model.Usuario;
import com.edificio.admin.model.enums.TipoRol;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class DashboardController {

    @FXML private Label lblUsuario;
    @FXML private Label lblRol;
    @FXML private BorderPane mainPane;

    // Nodos solo visibles para ADMIN
    @FXML private Menu      mnuGestion;
    @FXML private Button    btnResidentes;
    @FXML private Button    btnApartamentos;
    @FXML private Button    btnContratos;
    @FXML private Button    btnPagos;
    @FXML private Button    btnUsuarios;
    @FXML private Button    btnAlertas;
    @FXML private Separator sepAdmin;

    private Usuario usuarioActual;

    @FXML
    private void initialize() {}

    public void inicializar(Usuario usuario) {
        this.usuarioActual = usuario;
        lblUsuario.setText(usuario.getUsername());
        lblRol.setText(usuario.getRol().name());

        // Aplicar visibilidad según rol
        boolean esAdmin = (usuario.getRol() == TipoRol.ADMINISTRADOR);
        setAdminVisible(btnResidentes,    esAdmin);
        setAdminVisible(btnApartamentos,  esAdmin);
        setAdminVisible(btnContratos,     esAdmin);
        setAdminVisible(btnPagos,         esAdmin);
        setAdminVisible(btnUsuarios,      esAdmin);
        setAdminVisible(btnAlertas,       esAdmin);
        setAdminVisible(sepAdmin,         esAdmin);
        mnuGestion.setVisible(esAdmin);

        // Vista inicial según rol: ADMIN -> Residentes; PORTERO -> Escáner QR
        cargarVista(esAdmin ? "Residentes" : "EscannerQR");
    }

    /** Oculta el nodo y libera su espacio en el layout cuando no aplica el rol. */
    private void setAdminVisible(javafx.scene.Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    @FXML private void mostrarResidentes()  { cargarVista("Residentes"); }
    @FXML private void mostrarApartamentos() { cargarVista("Apartamentos"); }
    @FXML private void mostrarContratos()   { cargarVista("Contratos"); }
    @FXML private void mostrarPagos()       { cargarVista("Pagos"); }
    @FXML private void mostrarVisitas()     { cargarVista("RegistroVisitas"); }
    @FXML private void mostrarEscannerQR()  { cargarVista("EscannerQR"); }
    @FXML private void mostrarParqueaderos(){ cargarVista("Parqueaderos"); }
    @FXML private void mostrarUsuarios()    { cargarVista("Usuarios"); }
    @FXML private void mostrarAlertas()     { cargarVista("AlertasPago"); }

    private void cargarVista(String nombreVista) {
        try {
            String ruta = "/com/edificio/admin/view/vistas/" + nombreVista + ".fxml";
            FXMLLoader loader = new FXMLLoader(getClass().getResource(ruta));
            Parent vista = loader.load();
            mainPane.setCenter(vista);

            // Llamar inicializar() en cualquier controlador que lo implemente
            Object controller = loader.getController();
            if (controller instanceof ControladorVista) {
                ((ControladorVista) controller).inicializar();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error al cargar vista");
            alert.setHeaderText(null);
            alert.setContentText("No se pudo cargar la sección solicitada:\n" + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void cerrarSesion() {
        try {
            SesionUsuario.cerrarSesion();
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/edificio/admin/view/vistas/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Login - Sistema de Administración Residencial");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
