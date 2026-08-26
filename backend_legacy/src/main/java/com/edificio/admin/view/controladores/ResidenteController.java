package com.edificio.admin.view.controladores;

import com.edificio.admin.model.Residente;
import com.edificio.admin.model.Tutor;
import com.edificio.admin.model.Tutor.Parentesco;
import com.edificio.admin.dao.TipoDocumentoDAO;
import com.edificio.admin.dao.TutorDAO;
import com.edificio.admin.service.ResidenteService;
import com.edificio.admin.util.ValidadorCampos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResidenteController implements ControladorVista {

    // ---- Tabla ----
    @FXML private TableView<Residente>               tablaResidentes;
    @FXML private TableColumn<Residente, Integer>    colId;
    @FXML private TableColumn<Residente, String>     colNombres;
    @FXML private TableColumn<Residente, String>     colApellidos;
    @FXML private TableColumn<Residente, String>     colDocumento;
    @FXML private TableColumn<Residente, String>     colTelefono;
    @FXML private TableColumn<Residente, String>     colEmail;

    // ---- Formulario ----
    @FXML private TextField           txtNombres;
    @FXML private TextField           txtApellidos;
    @FXML private ComboBox<String>    cmbTipoDocumento;
    @FXML private TextField           txtDocumento;
    @FXML private TextField           txtTelefono;
    @FXML private TextField           txtEmail;
    @FXML private DatePicker          dpFechaNacimiento;
    @FXML private Label               lblEstadoEdad;

    // ---- Panel tutor (visible solo si chkMenorEdad está activo) ----
    @FXML private VBox                panelTutor;
    @FXML private TextField           txtTutorNombres;
    @FXML private TextField           txtTutorApellidos;
    @FXML private ComboBox<String>    cmbTutorTipoDoc;
    @FXML private TextField           txtTutorDocumento;
    @FXML private TextField           txtTutorTelefono;
    @FXML private TextField           txtTutorEmail;
    @FXML private ComboBox<Parentesco> cmbParentesco;

    private final ResidenteService service = new ResidenteService();
    private final TipoDocumentoDAO tipoDocDAO = new TipoDocumentoDAO();
    private final TutorDAO         tutorDAO   = new TutorDAO();
    private final ObservableList<Residente> lista = FXCollections.observableArrayList();

    /** Mapeo nombre legible -> id_tipo_doc (debe coincidir con TIPOS_DOCUMENTO en BD). */
    private final Map<String, Integer> tiposDoc = new LinkedHashMap<>();

    private Residente seleccionado;
    private Tutor     tutorSeleccionado; // tutor del residente actualmente cargado en formulario

    @FXML
    private void initialize() {
        cargarTiposDocumento();
        cmbParentesco.getItems().setAll(Parentesco.values());

        // ── Restricciones de entrada — residente ──────────────────────────
        ValidadorCampos.soloNombres(txtNombres,   30);
        ValidadorCampos.soloNombres(txtApellidos, 30);
        // Documento: tipo-dependiente (CC/TI → solo dígitos; CE/PP → alfanumérico)
        ValidadorCampos.aplicarFormatterDocumento(txtDocumento, null);
        cmbTipoDocumento.valueProperty().addListener(
                (obs, oldV, newV) -> ValidadorCampos.aplicarFormatterDocumento(txtDocumento, newV));
        ValidadorCampos.soloTelefono(txtTelefono, 20);
        ValidadorCampos.soloEmail(txtEmail);

        // ── Restricciones de entrada — tutor ──────────────────────────────
        ValidadorCampos.soloNombres(txtTutorNombres,   30);
        ValidadorCampos.soloNombres(txtTutorApellidos, 30);
        // Documento del tutor: también tipo-dependiente
        ValidadorCampos.aplicarFormatterDocumento(txtTutorDocumento, null);
        cmbTutorTipoDoc.valueProperty().addListener(
                (obs, oldV, newV) -> ValidadorCampos.aplicarFormatterDocumento(txtTutorDocumento, newV));
        ValidadorCampos.soloTelefono(txtTutorTelefono, 20);
        ValidadorCampos.soloEmail(txtTutorEmail);

        configurarTabla();
        cargarDatos();

        // ── Calcular automáticamente si es menor al cambiar la fecha de nacimiento ──
        dpFechaNacimiento.valueProperty().addListener(
                (obs, oldV, newV) -> actualizarEstadoEdad(newV));
    }

    /**
     * Recalcula el estado mayor/menor de edad a partir de la fecha de nacimiento
     * y muestra u oculta el panel de tutor según corresponda.
     */
    private void actualizarEstadoEdad(LocalDate fechaNac) {
        boolean menor = fechaNac != null
                && ChronoUnit.YEARS.between(fechaNac, LocalDate.now()) < 18;
        if (fechaNac == null) {
            lblEstadoEdad.setText("");
        } else if (menor) {
            lblEstadoEdad.setText("Menor de edad — complete los datos del tutor");
        } else {
            lblEstadoEdad.setText("Mayor de edad");
        }
        panelTutor.setVisible(menor);
        panelTutor.setManaged(menor);
        if (!menor) limpiarPanelTutor();
    }

    /** Devuelve true si la fecha de nacimiento indica que el residente es menor de 18 años. */
    private boolean calcularEsMenor() {
        LocalDate fn = dpFechaNacimiento.getValue();
        return fn != null && ChronoUnit.YEARS.between(fn, LocalDate.now()) < 18;
    }

    /** Carga los tipos de documento desde BD; usa fallback hardcodeado si falla la conexión. */
    private void cargarTiposDocumento() {
        try {
            tipoDocDAO.findActivos().forEach(t ->
                tiposDoc.put(t.getDescripcion() + " (" + t.getCodigo() + ")", t.getIdTipoDoc())
            );
        } catch (Exception e) {
            // Fallback con orden correcto según seed Oracle (CC=1, TI=2, CE=3, PP=4)
            tiposDoc.put("Cédula de Ciudadanía (CC)",    1);
            tiposDoc.put("Tarjeta de Identidad (TI)",    2);
            tiposDoc.put("Cédula de Extranjería (CE)",   3);
            tiposDoc.put("Pasaporte (PP)",               4);
        }
        cmbTipoDocumento.getItems().setAll(tiposDoc.keySet());
        cmbTutorTipoDoc.getItems().setAll(tiposDoc.keySet());
    }

    @Override
    public void inicializar() {
        cargarDatos();
        limpiarFormulario();
    }

    private void configurarTabla() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNombres.setCellValueFactory(new PropertyValueFactory<>("nombres"));
        colApellidos.setCellValueFactory(new PropertyValueFactory<>("apellidos"));
        colDocumento.setCellValueFactory(new PropertyValueFactory<>("numeroDocumento"));
        colTelefono.setCellValueFactory(new PropertyValueFactory<>("telefono"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        tablaResidentes.setItems(lista);

        tablaResidentes.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, nuevo) -> { if (nuevo != null) cargarEnFormulario(nuevo); });
    }

    private void cargarDatos() {
        try {
            List<Residente> residentes = service.listarTodos();
            lista.clear();
            lista.addAll(residentes);
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar residentes: " + e.getMessage());
        }
    }

    @FXML
    private void guardarResidente() {
        try {
            String tipoSelec = cmbTipoDocumento.getValue();
            if (tipoSelec == null) {
                mostrarAlerta("Advertencia", "Seleccione el tipo de documento.");
                return;
            }
            if (txtNombres.getText().isBlank()) {
                mostrarAlerta("Advertencia", "El nombre es obligatorio.");
                return;
            }
            if (txtApellidos.getText().isBlank()) {
                mostrarAlerta("Advertencia", "Los apellidos son obligatorios.");
                return;
            }
            if (txtDocumento.getText().isBlank()) {
                mostrarAlerta("Advertencia", "El número de documento es obligatorio.");
                return;
            }
            if (dpFechaNacimiento.getValue() == null) {
                mostrarAlerta("Advertencia", "La fecha de nacimiento es obligatoria.");
                return;
            }

            // Normalizar email antes de validar
            String emailRaw = ValidadorCampos.normalizarEmail(txtEmail.getText());
            if (!ValidadorCampos.validarEmail(emailRaw)) {
                mostrarAlerta("Advertencia",
                        "El email tiene un formato inválido (ej: nombre@dominio.com).");
                return;
            }

            Residente r = (seleccionado != null) ? seleccionado : new Residente();
            r.setNombres(ValidadorCampos.normalizarNombre(txtNombres.getText()));
            r.setApellidos(ValidadorCampos.normalizarNombre(txtApellidos.getText()));
            Integer idTipoDoc = tiposDoc.get(tipoSelec);
            if (idTipoDoc == null) {
                mostrarAlerta("Error", "Tipo de documento no reconocido. Vuelva a seleccionarlo o reinicie la aplicación.");
                return;
            }
            r.setIdTipoDoc(idTipoDoc);
            r.setNumeroDocumento(txtDocumento.getText().trim());
            r.setTelefono(txtTelefono.getText().trim());
            r.setEmail(emailRaw.isEmpty() ? null : emailRaw);
            r.setFechaNacimiento(dpFechaNacimiento.getValue());
            r.setEsMenorEdad(calcularEsMenor());

            Integer idResidente;
            if (seleccionado == null) {
                idResidente = service.registrar(r);
                mostrarAlerta("Éxito", "Residente registrado con ID: " + idResidente);
            } else {
                idResidente = r.getId();
                service.actualizar(r);
                mostrarAlerta("Éxito", "Residente actualizado correctamente.");
            }

            // ── Guardar/actualizar tutor si es menor de edad ──
            if (calcularEsMenor()) {
                guardarTutor(idResidente);
            }

            cargarDatos();
            limpiarFormulario();
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    /** Inserta o actualiza el tutor asociado al residente menor. */
    private void guardarTutor(Integer idResidenteMenor) {
        try {
            String tipoTutor = cmbTutorTipoDoc.getValue();
            Parentesco parentesco = cmbParentesco.getValue();

            if (txtTutorNombres.getText().isBlank() || txtTutorApellidos.getText().isBlank()
                    || tipoTutor == null || txtTutorDocumento.getText().isBlank()
                    || parentesco == null) {
                mostrarAlerta("Advertencia",
                        "Complete los datos del tutor (nombres, apellidos, documento y parentesco son obligatorios).");
                return;
            }

            String emailTutorRaw = ValidadorCampos.normalizarEmail(txtTutorEmail.getText());
            if (!ValidadorCampos.validarEmail(emailTutorRaw)) {
                mostrarAlerta("Advertencia",
                        "El email del tutor tiene un formato inválido (ej: nombre@dominio.com).");
                return;
            }

            Tutor t = (tutorSeleccionado != null) ? tutorSeleccionado : new Tutor();
            t.setIdResidenteMenor(idResidenteMenor);
            t.setIdTipoDoc(tiposDoc.get(tipoTutor));
            t.setNumeroDocumento(txtTutorDocumento.getText().trim());
            t.setNombres(ValidadorCampos.normalizarNombre(txtTutorNombres.getText()));
            t.setApellidos(ValidadorCampos.normalizarNombre(txtTutorApellidos.getText()));
            t.setTelefono(txtTutorTelefono.getText().trim());
            t.setEmail(emailTutorRaw.isEmpty() ? null : emailTutorRaw);
            t.setParentesco(parentesco);

            if (tutorSeleccionado == null) {
                tutorDAO.insert(t);
            } else {
                tutorDAO.update(t);
            }
        } catch (Exception e) {
            mostrarAlerta("Advertencia", "Residente guardado, pero hubo un error con el tutor: " + e.getMessage());
        }
    }

    @FXML
    private void editarResidente() {
        Residente sel = tablaResidentes.getSelectionModel().getSelectedItem();
        if (sel == null) { mostrarAlerta("Advertencia", "Seleccione un residente."); return; }
        cargarEnFormulario(sel);
    }

    @FXML
    private void eliminarResidente() {
        Residente sel = tablaResidentes.getSelectionModel().getSelectedItem();
        if (sel == null) { mostrarAlerta("Advertencia", "Seleccione un residente."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar desactivación");
        confirm.setContentText("¿Desactivar al residente " + sel.getNombreCompleto() + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                service.desactivar(sel.getId());
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
        tutorSeleccionado = null;
        txtNombres.clear();
        txtApellidos.clear();
        txtDocumento.clear();
        txtTelefono.clear();
        txtEmail.clear();
        cmbTipoDocumento.setValue(null);
        dpFechaNacimiento.setValue(null);
        lblEstadoEdad.setText("");
        tablaResidentes.getSelectionModel().clearSelection();
        limpiarPanelTutor();
        panelTutor.setVisible(false);
        panelTutor.setManaged(false);
    }

    private void limpiarPanelTutor() {
        tutorSeleccionado = null;
        txtTutorNombres.clear();
        txtTutorApellidos.clear();
        cmbTutorTipoDoc.setValue(null);
        txtTutorDocumento.clear();
        txtTutorTelefono.clear();
        txtTutorEmail.clear();
        cmbParentesco.setValue(null);
    }

    private void cargarEnFormulario(Residente r) {
        seleccionado = r;
        txtNombres.setText(r.getNombres());
        txtApellidos.setText(r.getApellidos());
        txtDocumento.setText(r.getNumeroDocumento());
        txtTelefono.setText(r.getTelefono() != null ? r.getTelefono() : "");
        txtEmail.setText(r.getEmail() != null ? r.getEmail() : "");
        dpFechaNacimiento.setValue(r.getFechaNacimiento()); // dispara actualizarEstadoEdad()

        // Buscar la clave del tipo de doc por su valor ID
        tiposDoc.forEach((nombre, id) -> {
            if (id.equals(r.getIdTipoDoc())) cmbTipoDocumento.setValue(nombre);
        });

        // Si el panel de tutor quedó visible (menor de edad), cargar sus datos
        if (panelTutor.isVisible()) {
            cargarTutorEnFormulario(r.getId());
        } else {
            limpiarPanelTutor();
        }
    }

    private void cargarTutorEnFormulario(Integer idResidente) {
        try {
            tutorSeleccionado = tutorDAO.findByResidenteMenor(idResidente);
            if (tutorSeleccionado == null) return;

            txtTutorNombres.setText(tutorSeleccionado.getNombres());
            txtTutorApellidos.setText(tutorSeleccionado.getApellidos());
            txtTutorDocumento.setText(tutorSeleccionado.getNumeroDocumento());
            txtTutorTelefono.setText(tutorSeleccionado.getTelefono() != null ? tutorSeleccionado.getTelefono() : "");
            txtTutorEmail.setText(tutorSeleccionado.getEmail() != null ? tutorSeleccionado.getEmail() : "");
            cmbParentesco.setValue(tutorSeleccionado.getParentesco());
            tiposDoc.forEach((nombre, id) -> {
                if (id.equals(tutorSeleccionado.getIdTipoDoc()))
                    cmbTutorTipoDoc.setValue(nombre);
            });
        } catch (Exception e) {
            mostrarAlerta("Advertencia", "No se pudo cargar el tutor: " + e.getMessage());
        }
    }

    private void mostrarAlerta(String titulo, String contenido) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
