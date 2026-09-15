package com.edificio.admin.view.controladores;

import com.edificio.admin.dao.ParqueaderoDAO;
import com.edificio.admin.model.Parqueadero;
import com.edificio.admin.model.enums.EstadoParqueadero;
import com.edificio.admin.model.enums.TipoParqueadero;
import com.edificio.admin.service.ParqueaderoService;
import com.edificio.admin.util.ValidadorCampos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

/**
 * CRUD de parqueaderos del edificio.
 * Estado DISPONIBLE/OCUPADO es gestionado automáticamente por el trigger
 * TRG_ACCESO_SALIDA en Oracle; aquí solo se administra el catálogo.
 */
public class ParqueaderoController implements ControladorVista {

    // ---- Tabla ----
    @FXML private TableView<Parqueadero>                      tablaParqueaderos;
    @FXML private TableColumn<Parqueadero, Integer>           colId;
    @FXML private TableColumn<Parqueadero, String>            colCodigo;
    @FXML private TableColumn<Parqueadero, TipoParqueadero>   colTipo;
    @FXML private TableColumn<Parqueadero, String>            colVisitante;
    @FXML private TableColumn<Parqueadero, EstadoParqueadero> colEstado;
    @FXML private TableColumn<Parqueadero, String>            colApartamento;
    @FXML private TableColumn<Parqueadero, String>            colPropietario;

    // ---- Formulario ----
    @FXML private TextField                  txtCodigo;
    @FXML private ComboBox<TipoParqueadero>  cmbTipo;
    @FXML private CheckBox                   chkEsVisitante;
    @FXML private ComboBox<EstadoParqueadero> cmbEstado;

    // ---- Filtros ----
    @FXML private ComboBox<String>          cmbFiltroEstado;
    @FXML private ComboBox<String>          cmbFiltroTipo;
    @FXML private ComboBox<String>          cmbFiltroClasificacion;

    private final ParqueaderoService parqueaderoService = new ParqueaderoService();
    private final ParqueaderoDAO             parqueaderoDAO   = new ParqueaderoDAO();
    private final ObservableList<Parqueadero> lista = FXCollections.observableArrayList();

    /** Parqueadero actualmente seleccionado para edición; null si es nuevo. */
    private Parqueadero seleccionado = null;

    @FXML
    private void initialize() {
        cmbTipo.getItems().addAll(TipoParqueadero.values());
        cmbEstado.getItems().addAll(EstadoParqueadero.values());
        cmbEstado.setValue(EstadoParqueadero.DISPONIBLE);

        // Inicializar filtros
        cmbFiltroEstado.getItems().addAll("", "DISPONIBLE", "OCUPADO", "EN_MANTENIMIENTO");
        cmbFiltroEstado.setValue("");
        cmbFiltroTipo.getItems().addAll("", "VEHICULO", "MOTO", "BICICLETA");
        cmbFiltroTipo.setValue("");
        cmbFiltroClasificacion.getItems().addAll("", "Propietario", "Visitante");
        cmbFiltroClasificacion.setValue("");

        // Listeners para filtros automáticos
        cmbFiltroEstado.valueProperty().addListener((obs, old, newV) -> aplicarFiltros());
        cmbFiltroTipo.valueProperty().addListener((obs, old, newV) -> aplicarFiltros());
        cmbFiltroClasificacion.valueProperty().addListener((obs, old, newV) -> aplicarFiltros());

        // ── Restricciones de entrada ──────────────────────────────────────
        ValidadorCampos.limitar(txtCodigo, 20);   // PARQUEADEROS.codigo VARCHAR2(20)

        configurarTabla();
        cargarDatos();

        // Al seleccionar fila → cargar en formulario
        tablaParqueaderos.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, nuevo) -> {
                    if (nuevo != null) cargarEnFormulario(nuevo);
                });
    }

    private void aplicarFiltros() {
        String estado = cmbFiltroEstado.getValue();
        String tipo = cmbFiltroTipo.getValue();
        Boolean esVisitante = null;
        String clasif = cmbFiltroClasificacion.getValue();
        if (clasif != null && !clasif.isEmpty()) {
            esVisitante = "Visitante".equals(clasif);
        }
        try {
            if (estado == null || estado.isEmpty() && (tipo == null || tipo.isEmpty()) && esVisitante == null) {
                lista.setAll(parqueaderoService.listarTodos());
            } else {
                lista.setAll(parqueaderoDAO.findConFiltros(
                        estado.isEmpty() ? null : estado,
                        tipo.isEmpty() ? null : tipo,
                        esVisitante));
            }
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al filtrar: " + e.getMessage());
        }
    }

    @Override
    public void inicializar() {
        cargarDatos();
    }

    // ---- Tabla ----

    private void configurarTabla() {
        colId.setCellValueFactory(new PropertyValueFactory<>("idParqueadero"));
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        colApartamento.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getNumeroApartamento() != null
                                ? data.getValue().getNumeroApartamento() : "—"));
        colPropietario.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getNombrePropietario() != null
                                ? data.getValue().getNombrePropietario() : "Visitantes"));

        // Columna esVisitante — texto "Sí" / "No"
        colVisitante.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    Parqueadero p = (Parqueadero) getTableRow().getItem();
                    setText(p.isEsVisitante() ? "Sí" : "No");
                }
            }
        });

        tablaParqueaderos.setItems(lista);
    }

    private void cargarDatos() {
        try {
            List<Parqueadero> todos = parqueaderoService.listarTodos();
            lista.setAll(todos);
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar parqueaderos: " + e.getMessage());
        }
    }

    // ---- Acciones CRUD ----

    @FXML
    private void guardar() {
        try {
            String codigo = txtCodigo.getText().trim();
            TipoParqueadero tipo = cmbTipo.getValue();
            EstadoParqueadero estado = cmbEstado.getValue();

            if (codigo.isEmpty()) {
                mostrarAlerta("Advertencia", "El código del parqueadero es obligatorio.");
                return;
            }
            if (tipo == null) {
                mostrarAlerta("Advertencia", "Seleccione el tipo de parqueadero.");
                return;
            }

            if (seleccionado == null) {
                // --- Nuevo parqueadero ---
                Parqueadero nuevo = new Parqueadero();
                nuevo.setCodigo(codigo);
                nuevo.setTipo(tipo);
                nuevo.setEsVisitante(chkEsVisitante.isSelected());
                nuevo.setEstado(estado != null ? estado : EstadoParqueadero.DISPONIBLE);
                parqueaderoService.registrar(nuevo);
                mostrarAlerta("Éxito", "Parqueadero registrado correctamente.");
            } else {
                // --- Actualizar existente ---
                seleccionado.setCodigo(codigo);
                seleccionado.setTipo(tipo);
                seleccionado.setEsVisitante(chkEsVisitante.isSelected());
                seleccionado.setEstado(estado != null ? estado : EstadoParqueadero.DISPONIBLE);
                parqueaderoService.actualizar(seleccionado);
                mostrarAlerta("Éxito", "Parqueadero actualizado correctamente.");
            }

            cargarDatos();
            limpiarFormulario();

        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    @FXML
    private void desactivar() {
        Parqueadero sel = tablaParqueaderos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta("Advertencia", "Seleccione un parqueadero de la tabla.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar desactivación");
        confirm.setContentText("¿Desactivar el parqueadero \"" + sel.getCodigo() + "\"?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                parqueaderoService.desactivar(sel.getIdParqueadero());
                cargarDatos();
                limpiarFormulario();
                mostrarAlerta("Información", "Parqueadero desactivado.");
            } catch (Exception e) {
                mostrarAlerta("Error", e.getMessage());
            }
        }
    }

    @FXML
    private void limpiarFormulario() {
        seleccionado = null;
        txtCodigo.clear();
        cmbTipo.setValue(null);
        chkEsVisitante.setSelected(false);
        cmbEstado.setValue(EstadoParqueadero.DISPONIBLE);
        tablaParqueaderos.getSelectionModel().clearSelection();
    }

    // ---- Helpers ----

    private void cargarEnFormulario(Parqueadero p) {
        seleccionado = p;
        txtCodigo.setText(p.getCodigo());
        cmbTipo.setValue(p.getTipo());
        chkEsVisitante.setSelected(p.isEsVisitante());
        cmbEstado.setValue(p.getEstado());
    }

    private void mostrarAlerta(String titulo, String contenido) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
