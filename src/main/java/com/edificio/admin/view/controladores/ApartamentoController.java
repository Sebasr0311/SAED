package com.edificio.admin.view.controladores;

import com.edificio.admin.model.Apartamento;
import com.edificio.admin.model.enums.EstadoApartamento;
import com.edificio.admin.service.ApartamentoService;
import com.edificio.admin.util.ValidadorCampos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.util.List;

public class ApartamentoController implements ControladorVista {

    // ---- Tabla ----
    @FXML private TableView<Apartamento>                   tablaApartamentos;
    @FXML private TableColumn<Apartamento, Integer>        colId;
    @FXML private TableColumn<Apartamento, String>         colNumero;
    @FXML private TableColumn<Apartamento, Integer>        colPiso;
    @FXML private TableColumn<Apartamento, String>         colTipo;
    @FXML private TableColumn<Apartamento, BigDecimal>     colArea;
    @FXML private TableColumn<Apartamento, EstadoApartamento> colEstado;

    // ---- Formulario ----
    @FXML private TextField                  txtNumero;
    @FXML private TextField                  txtPiso;
    @FXML private ComboBox<String>           cmbTipo;
    @FXML private TextField                  txtArea;
    @FXML private ComboBox<EstadoApartamento> cmbEstado;

    private final ApartamentoService service = new ApartamentoService();
    private final ObservableList<Apartamento> lista = FXCollections.observableArrayList();
    private Apartamento seleccionado;

    @FXML
    private void initialize() {
        // Valores exactos del CHECK constraint en DDL Oracle
        cmbTipo.getItems().addAll("ESTUDIO", "1_HAB", "2_HAB", "3_HAB", "PENTHOUSE", "OTRO");
        cmbEstado.getItems().addAll(EstadoApartamento.values());

        // ── Restricciones de entrada ──────────────────────────────────────
        ValidadorCampos.limitar(txtNumero, 20);
        ValidadorCampos.soloDigitos(txtPiso, 3);
        ValidadorCampos.soloDecimalPositivo(txtArea);

        configurarTabla();
        cargarDatos();
    }

    @Override
    public void inicializar() {
        cargarDatos();
        limpiarFormulario();
    }

    private void configurarTabla() {
        colId.setCellValueFactory(new PropertyValueFactory<>("idApartamento"));
        colNumero.setCellValueFactory(new PropertyValueFactory<>("numero"));
        colPiso.setCellValueFactory(new PropertyValueFactory<>("piso"));
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colArea.setCellValueFactory(new PropertyValueFactory<>("areaM2"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        tablaApartamentos.setItems(lista);

        tablaApartamentos.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, nuevo) -> { if (nuevo != null) cargarEnFormulario(nuevo); });
    }

    private void cargarDatos() {
        try {
            List<Apartamento> apartamentos = service.listarTodos();
            lista.clear();
            lista.addAll(apartamentos);
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar apartamentos: " + e.getMessage());
        }
    }

    @FXML
    private void guardarApartamento() {
        try {
            if (txtNumero.getText().isBlank()) {
                mostrarAlerta("Advertencia", "El número del apartamento es obligatorio.");
                return;
            }
            if (cmbTipo.getValue() == null) {
                mostrarAlerta("Advertencia", "Seleccione el tipo de apartamento.");
                return;
            }
            if (txtPiso.getText().isBlank()) {
                mostrarAlerta("Advertencia", "El piso es obligatorio.");
                return;
            }
            if (txtArea.getText().isBlank()) {
                mostrarAlerta("Advertencia", "El área es obligatoria.");
                return;
            }

            Apartamento a = (seleccionado != null) ? seleccionado : new Apartamento();
            a.setNumero(txtNumero.getText().trim());
            a.setPiso(Integer.parseInt(txtPiso.getText().trim()));
            a.setTipo(cmbTipo.getValue());
            a.setAreaM2(new BigDecimal(txtArea.getText().trim().replace(",", ".")));
            if (cmbEstado.getValue() != null) a.setEstado(cmbEstado.getValue());

            if (seleccionado == null) {
                Integer id = service.registrar(a);
                mostrarAlerta("Éxito", "Apartamento registrado con ID: " + id);
            } else {
                service.actualizar(a);
                mostrarAlerta("Éxito", "Apartamento actualizado.");
            }
            cargarDatos();
            limpiarFormulario();
        } catch (NumberFormatException ex) {
            mostrarAlerta("Error", "Piso y área deben ser numéricos.");
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    @FXML
    private void editarApartamento() {
        Apartamento sel = tablaApartamentos.getSelectionModel().getSelectedItem();
        if (sel == null) { mostrarAlerta("Advertencia", "Seleccione un apartamento."); return; }
        cargarEnFormulario(sel);
    }

    @FXML
    private void eliminarApartamento() {
        Apartamento sel = tablaApartamentos.getSelectionModel().getSelectedItem();
        if (sel == null) { mostrarAlerta("Advertencia", "Seleccione un apartamento."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar desactivación");
        confirm.setContentText("¿Desactivar el apartamento " + sel.getNumero() + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                service.desactivar(sel.getIdApartamento());
                cargarDatos();
                limpiarFormulario();
            } catch (Exception e) {
                mostrarAlerta("Error", e.getMessage());
            }
        }
    }

    @FXML
    private void limpiarFormulario() {
        seleccionado = null;
        txtNumero.clear();
        txtPiso.clear();
        txtArea.clear();
        cmbTipo.setValue(null);
        cmbEstado.setValue(null);
        tablaApartamentos.getSelectionModel().clearSelection();
    }

    private void cargarEnFormulario(Apartamento a) {
        seleccionado = a;
        txtNumero.setText(a.getNumero());
        txtPiso.setText(String.valueOf(a.getPiso()));
        txtArea.setText(a.getAreaM2() != null ? a.getAreaM2().toPlainString() : "");
        cmbTipo.setValue(a.getTipo());
        cmbEstado.setValue(a.getEstado());
    }

    private void mostrarAlerta(String titulo, String contenido) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
