package com.edificio.admin.view.controladores;

import com.edificio.admin.config.SesionUsuario;
import com.edificio.admin.model.Apartamento;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.Residente;
import com.edificio.admin.model.enums.EstadoContrato;
import com.edificio.admin.service.ApartamentoService;
import com.edificio.admin.service.ContratoService;
import com.edificio.admin.service.ResidenteService;
import com.edificio.admin.util.ValidadorCampos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ContratoController implements ControladorVista {

    // ---- Tabla ----
    @FXML private TableView<Contrato>                 tablaContratos;
    @FXML private TableColumn<Contrato, Integer>      colId;
    @FXML private TableColumn<Contrato, Integer>      colApartamento;
    @FXML private TableColumn<Contrato, LocalDate>    colFechaInicio;
    @FXML private TableColumn<Contrato, LocalDate>    colFechaFin;
    @FXML private TableColumn<Contrato, BigDecimal>   colValor;
    @FXML private TableColumn<Contrato, EstadoContrato> colEstado;

    // ---- Formulario ----
    @FXML private ComboBox<Apartamento> cmbApartamento;
    @FXML private ComboBox<Residente>   cmbResidente;
    @FXML private DatePicker            dpFechaInicio;
    @FXML private DatePicker            dpFechaFin;
    @FXML private TextField             txtValorMensual;
    @FXML private TextArea              txtNotas;

    private final ContratoService    contratoService    = new ContratoService();
    private final ApartamentoService apartamentoService = new ApartamentoService();
    private final ResidenteService   residenteService   = new ResidenteService();
    private final ObservableList<Contrato> lista = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // ── Restricciones de entrada ──────────────────────────────────────
        ValidadorCampos.soloDecimalPositivo(txtValorMensual);
        ValidadorCampos.limitar(txtNotas, 1000);   // CONTRATOS.observaciones VARCHAR2(1000)

        configurarTabla();
        cargarCombos();
        cargarDatos();
    }

    @Override
    public void inicializar() {
        cargarCombos();
        cargarDatos();
        limpiarFormulario();
    }

    private void configurarTabla() {
        colId.setCellValueFactory(new PropertyValueFactory<>("idContrato"));
        colApartamento.setCellValueFactory(new PropertyValueFactory<>("idApartamento"));
        colFechaInicio.setCellValueFactory(new PropertyValueFactory<>("fechaInicio"));
        colFechaFin.setCellValueFactory(new PropertyValueFactory<>("fechaFin"));
        colValor.setCellValueFactory(new PropertyValueFactory<>("valorMensual"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        tablaContratos.setItems(lista);
    }

    private void cargarCombos() {
        try {
            List<Apartamento> apartamentos = apartamentoService.listarTodos();
            cmbApartamento.setItems(FXCollections.observableArrayList(apartamentos));
            List<Residente> residentes = residenteService.listarTodos();
            cmbResidente.setItems(FXCollections.observableArrayList(residentes));
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar combos: " + e.getMessage());
        }
    }

    private void cargarDatos() {
        try {
            lista.clear();
            lista.addAll(contratoService.listarTodos());
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar contratos: " + e.getMessage());
        }
    }

    @FXML
    private void crearContrato() {
        try {
            Apartamento apt = cmbApartamento.getValue();
            Residente   res = cmbResidente.getValue();
            if (apt == null) { mostrarAlerta("Advertencia", "Seleccione un apartamento."); return; }
            if (res == null) { mostrarAlerta("Advertencia", "Seleccione el residente arrendatario."); return; }
            if (txtValorMensual.getText().isBlank()) {
                mostrarAlerta("Advertencia", "El valor mensual es obligatorio.");
                return;
            }

            Contrato c = new Contrato();
            c.setIdApartamento(apt.getIdApartamento());
            c.setFechaInicio(dpFechaInicio.getValue() != null ? dpFechaInicio.getValue() : LocalDate.now());
            c.setFechaFin(dpFechaFin.getValue());
            c.setValorMensual(new BigDecimal(txtValorMensual.getText().trim().replace(",", "")));
            c.setNotas(txtNotas.getText().trim());
            // Auditoría: usuario que registra el contrato (NOT NULL en BD)
            if (SesionUsuario.getUsuarioActual() != null)
                c.setIdRegistradoPor(SesionUsuario.getUsuarioActual().getIdUsuario());

            Integer id = contratoService.crearContrato(c, res.getId());
            mostrarAlerta("Éxito", "Contrato creado con ID: " + id + "\nEstado: PENDIENTE_FIRMA");
            cargarDatos();
            limpiarFormulario();
        } catch (NumberFormatException ex) {
            mostrarAlerta("Error", "El valor mensual debe ser un número.");
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    @FXML
    private void activarContrato() {
        Contrato sel = tablaContratos.getSelectionModel().getSelectedItem();
        if (sel == null) { mostrarAlerta("Advertencia", "Seleccione un contrato."); return; }
        try {
            contratoService.activar(sel.getIdContrato());
            mostrarAlerta("Éxito", "Contrato activado. Apartamento marcado OCUPADO.");
            cargarDatos();
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    @FXML
    private void cancelarContrato() {
        Contrato sel = tablaContratos.getSelectionModel().getSelectedItem();
        if (sel == null) { mostrarAlerta("Advertencia", "Seleccione un contrato."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar cancelación");
        confirm.setContentText("¿Cancelar el contrato #" + sel.getIdContrato() + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                contratoService.cancelar(sel.getIdContrato());
                mostrarAlerta("Éxito", "Contrato cancelado. Apartamento marcado DISPONIBLE.");
                cargarDatos();
            } catch (Exception e) {
                mostrarAlerta("Error", e.getMessage());
            }
        }
    }

    @FXML
    private void limpiarFormulario() {
        cmbApartamento.setValue(null);
        cmbResidente.setValue(null);
        dpFechaInicio.setValue(null);
        dpFechaFin.setValue(null);
        txtValorMensual.clear();
        txtNotas.clear();
        tablaContratos.getSelectionModel().clearSelection();
    }

    private void mostrarAlerta(String titulo, String contenido) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
