package com.edificio.admin.view.controladores;

import com.edificio.admin.dao.AlertaPagoDAO;
import com.edificio.admin.model.AlertaPago;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controlador para la vista de Alertas de Pago (solo lectura + marcar leída).
 * Muestra alertas generadas automáticamente por Oracle (triggers).
 */
public class AlertasPagoController implements ControladorVista {

    @FXML private TableView<AlertaPago>                    tablaAlertas;
    @FXML private TableColumn<AlertaPago, Integer>         colAlertaId;
    @FXML private TableColumn<AlertaPago, String>          colAlertaTipo;
    @FXML private TableColumn<AlertaPago, String>          colAlertaApto;
    @FXML private TableColumn<AlertaPago, String>          colAlertaRes;
    @FXML private TableColumn<AlertaPago, String>          colAlertaPeriodo;
    @FXML private TableColumn<AlertaPago, String>          colAlertaEstado;
    @FXML private TableColumn<AlertaPago, String>          colAlertaCanal;
    @FXML private TableColumn<AlertaPago, String>          colAlertaLeida;
    @FXML private TableColumn<AlertaPago, LocalDateTime>   colAlertaEnvio;

    @FXML private CheckBox chkSoloNoLeidas;

    private final AlertaPagoDAO dao = new AlertaPagoDAO();
    private final ObservableList<AlertaPago> lista = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        configurarTabla();
        cargarDatos();
    }

    @Override
    public void inicializar() {
        cargarDatos();
    }

    // ── Tabla ─────────────────────────────────────────────────────────────────

    private void configurarTabla() {
        colAlertaId.setCellValueFactory(new PropertyValueFactory<>("idAlerta"));
        colAlertaTipo.setCellValueFactory(new PropertyValueFactory<>("tipoAlerta"));
        colAlertaApto.setCellValueFactory(new PropertyValueFactory<>("numeroApartamento"));
        colAlertaRes.setCellValueFactory(new PropertyValueFactory<>("nombreResidente"));
        colAlertaPeriodo.setCellValueFactory(new PropertyValueFactory<>("periodo"));
        colAlertaEstado.setCellValueFactory(new PropertyValueFactory<>("estadoCuota"));
        colAlertaCanal.setCellValueFactory(new PropertyValueFactory<>("canal"));
        colAlertaEnvio.setCellValueFactory(new PropertyValueFactory<>("enviadaEn"));

        // Columna Leída — Sí / No con estilo visual
        colAlertaLeida.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setStyle("");
                } else {
                    AlertaPago a = (AlertaPago) getTableRow().getItem();
                    if (a.isLeida()) {
                        setText("Sí");
                        setStyle("-fx-text-fill: #7f8c8d;");
                    } else {
                        setText("No");
                        setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                    }
                }
            }
        });

        tablaAlertas.setItems(lista);
    }

    private void cargarDatos() {
        try {
            List<AlertaPago> datos = chkSoloNoLeidas.isSelected()
                    ? dao.findNoLeidas()
                    : dao.findAll();
            lista.setAll(datos);
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudieron cargar las alertas: " + e.getMessage());
        }
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    @FXML
    private void filtrarAlertas() {
        cargarDatos();
    }

    @FXML
    private void refrescar() {
        cargarDatos();
    }

    @FXML
    private void marcarLeida() {
        AlertaPago sel = tablaAlertas.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Advertencia", "Seleccione una alerta de la lista.");
            return;
        }
        if (sel.isLeida()) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Información", "Esta alerta ya fue marcada como leída.");
            return;
        }
        try {
            dao.marcarLeida(sel.getIdAlerta());
            cargarDatos();
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudo marcar la alerta: " + e.getMessage());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String msg) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
