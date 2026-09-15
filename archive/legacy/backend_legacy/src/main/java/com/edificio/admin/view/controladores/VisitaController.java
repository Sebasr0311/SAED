package com.edificio.admin.view.controladores;

import com.edificio.admin.config.SesionUsuario;
import com.edificio.admin.dao.ContratoResidenteDAO;
import com.edificio.admin.dao.ParqueaderoDAO;
import com.edificio.admin.dao.TipoDocumentoDAO;
import com.edificio.admin.dao.VisitanteDAO;
import com.edificio.admin.dao.VisitanteFrecuenteDAO;
import com.edificio.admin.model.ContratoResidente;
import com.edificio.admin.model.Parqueadero;
import com.edificio.admin.model.Residente;
import com.edificio.admin.model.Usuario;
import com.edificio.admin.model.VehiculoVisita;
import com.edificio.admin.model.Visita;
import com.edificio.admin.model.Visitante;
import com.edificio.admin.model.VisitanteFrecuente;
import com.edificio.admin.model.enums.TipoRol;
import com.edificio.admin.model.enums.TipoVehiculo;
import com.edificio.admin.service.NotificacionService;
import com.edificio.admin.service.ResidenteService;
import com.edificio.admin.service.VisitaService;
import com.edificio.admin.util.GeneradorQR;
import com.edificio.admin.util.ValidadorCampos;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VisitaController implements ControladorVista {

    // =========================================================
    // FXML — Tab 1: Nueva Visita
    // =========================================================

    @FXML private TabPane                                  tabPaneVisitas;

    // ---- Tabla visitas activas ----
    @FXML private TableView<Visita>                        tablaVisitas;
    @FXML private TableColumn<Visita, Integer>             colIdVisita;
    @FXML private TableColumn<Visita, Integer>             colResidente;
    @FXML private TableColumn<Visita, LocalDateTime>       colFechaRegistro;
    @FXML private TableColumn<Visita, Integer>             colCantPersonas;
    @FXML private TableColumn<Visita, String>              colEstadoVisita;

    // ---- Formulario visitante ----
    @FXML private TextField           txtNombresVisitante;
    @FXML private TextField           txtApellidosVisitante;
    @FXML private ComboBox<String>    cmbTipoDocVisitante;
    @FXML private TextField           txtDocVisitante;
    @FXML private Label              lblDocPdfSeleccionado;
    @FXML private TextField           txtTelefonoVisitante;
    @FXML private TextField           txtEmailVisitante;

    private String pathDocPdf;

    // ---- Formulario visita ----
    @FXML private ComboBox<Residente> cmbResidente;
    @FXML private Spinner<Integer>    spnTiempoValidez;
    @FXML private Spinner<Integer>    spnCantPersonas;
    @FXML private TextArea            txtNotasVisita;

    // ---- Vehículo (opcional) ----
    @FXML private TextField              txtPlacaVehiculo;
    @FXML private ComboBox<TipoVehiculo> cmbTipoVehiculo;

    // ---- Parqueaderos para visitantes (Tab 1, solo lectura) ----
    @FXML private TableView<Parqueadero>           tablaParqueaderosVisitantes;
    @FXML private TableColumn<Parqueadero, String> colPrkVCodigo;
    @FXML private TableColumn<Parqueadero, String> colPrkVTipo;
    @FXML private TableColumn<Parqueadero, String> colPrkVEstado;

    // =========================================================
    // FXML — Tab 2: Visita Rápida – Frecuentes
    // =========================================================

    @FXML private Tab                                           tabFrecuentes;
    @FXML private ComboBox<Residente>                           cmbResidenteFrecuente;
    @FXML private TableView<VisitanteFrecuente>                 tablaFrecuentes;
    @FXML private TableColumn<VisitanteFrecuente, String>       colNombreFrec;
    @FXML private TableColumn<VisitanteFrecuente, String>       colDocumentoFrec;
    @FXML private TableColumn<VisitanteFrecuente, Integer>      colTotalVisitas;
    @FXML private TableColumn<VisitanteFrecuente, LocalDateTime> colUltimaVisita;
    @FXML private Spinner<Integer>                              spnCantPersonasFrecuente;
    @FXML private Spinner<Integer>                              spnTiempoValidezFrecuente;
    @FXML private TextField                                     txtPlacaFrecuente;
    @FXML private ComboBox<TipoVehiculo>                        cmbTipoVehiculoFrecuente;
    @FXML private TextField                                     txtDescripcionTipoFrecuente;
    @FXML private TextArea                                      txtNotasFrecuente;
    @FXML private Label                                         lblCodigoQRFrecuente;
    @FXML private ImageView                                     imgQRFrecuente;
    /** Muestra en itálica el modo de llegada de la última visita registrada. */
    @FXML private Label                                         lblUltimoVehiculoFrec;

    // =========================================================
    // Estado interno compartido (compartir QR)
    // =========================================================

    private String ultimoCodigoQR          = null;
    private String telefonoUltimoVisitante = null;
    private String emailUltimoVisitante    = null;

    // =========================================================
    // Servicios y DAOs
    // =========================================================

    private final VisitaService            visitaService         = new VisitaService();
    private final ResidenteService         residenteService      = new ResidenteService();
    private final VisitanteDAO             visitanteDAO          = new VisitanteDAO();
    private final ContratoResidenteDAO     contratoResidenteDAO  = new ContratoResidenteDAO();
    private final NotificacionService      notificacionService   = new NotificacionService();
    private final GeneradorQR              generadorQR           = new GeneradorQR();
    private final TipoDocumentoDAO         tipoDocDAO            = new TipoDocumentoDAO();
    private final VisitanteFrecuenteDAO    frecuenteDAO          = new VisitanteFrecuenteDAO();
    private final ParqueaderoDAO           parqueaderoDAO        = new ParqueaderoDAO();

    // =========================================================
    // Listas observables
    // =========================================================

    private final ObservableList<Visita>             lista                       = FXCollections.observableArrayList();
    private final ObservableList<VisitanteFrecuente> listaFrecuentes             = FXCollections.observableArrayList();
    private final ObservableList<Parqueadero>        listaParqueaderosVisitantes = FXCollections.observableArrayList();

    private final Map<String, Integer> tiposDoc = new LinkedHashMap<>();

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    // =========================================================
    // Inicialización
    // =========================================================

    @FXML
    private void initialize() {
        cargarTiposDocumento();
        cmbTipoVehiculo.getItems().addAll(TipoVehiculo.values());

        // ── Restricciones de entrada — campos del visitante ───────────────
        ValidadorCampos.soloNombres(txtNombresVisitante,   30);  // VISITANTES.nombres   VARCHAR2(100)
        ValidadorCampos.soloNombres(txtApellidosVisitante, 30);  // VISITANTES.apellidos VARCHAR2(100)
        // Documento: tipo-dependiente (CC/TI → dígitos; CE/PP → alfanumérico)
        ValidadorCampos.aplicarFormatterDocumento(txtDocVisitante, null);
        cmbTipoDocVisitante.valueProperty().addListener(
                (obs, oldV, newV) -> ValidadorCampos.aplicarFormatterDocumento(txtDocVisitante, newV));
        ValidadorCampos.soloTelefono(txtTelefonoVisitante,  20);  // VISITANTES.telefono  VARCHAR2(20)
        ValidadorCampos.soloEmail(txtEmailVisitante);              // VISITANTES.email     VARCHAR2(254)
        // Placas de vehículo: alfanumérico (ej: ABC123)
        ValidadorCampos.soloAlfanumerico(txtPlacaVehiculo,    10); // VEHICULOS_VISITA.placa VARCHAR2(10)
        ValidadorCampos.soloAlfanumerico(txtPlacaFrecuente,   10);

        spnTiempoValidez.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 60, 30));
        spnCantPersonas.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1));

        // ---- StringConverter para ComboBox de residentes (Tab 1 y Tab 2) ----
        StringConverter<Residente> resConverter = new StringConverter<>() {
            @Override public String toString(Residente r)    { return r == null ? "" : r.getNombreCompleto(); }
            @Override public Residente fromString(String s)  { return null; }
        };
        cmbResidente.setConverter(resConverter);
        cmbResidenteFrecuente.setConverter(resConverter);

        configurarTabla();
        configurarTablaFrecuentes();
        configurarTablaParqueaderos();
        cargarResidentes();
        cargarVisitasActivas();
        cargarParqueaderosVisitantes();

        // ---- Spinners Tab 2 ----
        spnCantPersonasFrecuente.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1));
        spnTiempoValidezFrecuente.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 60, 30));

        // ---- TipoVehiculo en Tab 2 (primer ítem = null = a pie) ----
        cmbTipoVehiculoFrecuente.getItems().add(null);
        cmbTipoVehiculoFrecuente.getItems().addAll(TipoVehiculo.values());
        cmbTipoVehiculoFrecuente.setConverter(new StringConverter<>() {
            @Override public String toString(TipoVehiculo t)    { return t == null ? "(Sin vehículo — a pie)" : t.name(); }
            @Override public TipoVehiculo fromString(String s)  { return null; }
        });

        // ---- Listener: al seleccionar residente en Tab 2 → cargar frecuentes ----
        cmbResidenteFrecuente.valueProperty().addListener((obs, oldV, newV) ->
            cargarFrecuentes(newV != null ? newV.getId() : null)
        );

        // ---- Listener: al seleccionar fila en tablaFrecuentes → pre-llenar form ----
        tablaFrecuentes.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldV, newV) -> { if (newV != null) preLlenarFormFrecuente(newV); }
        );

        // ---- Ocultar tab frecuentes para PORTERO ----
        Usuario u = SesionUsuario.getUsuarioActual();
        if (u == null || u.getRol() == TipoRol.PORTERO) {
            tabPaneVisitas.getTabs().remove(tabFrecuentes);
        }
    }

    @Override
    public void inicializar() {
        cargarResidentes();
        cargarVisitasActivas();
        cargarParqueaderosVisitantes();
    }

    // =========================================================
    // Configuración de tablas
    // =========================================================

    /** Carga los tipos de documento desde BD; usa fallback hardcodeado si falla la conexión. */
    private void cargarTiposDocumento() {
        try {
            tipoDocDAO.findActivos().forEach(t ->
                tiposDoc.put(t.getDescripcion() + " (" + t.getCodigo() + ")", t.getIdTipoDoc())
            );
        } catch (Exception e) {
            // Fallback con orden correcto según seed Oracle (CC=1, TI=2, CE=3, PP=4)
            tiposDoc.put("Cédula de Ciudadanía (CC)", 1);
            tiposDoc.put("Tarjeta de Identidad (TI)", 2);
            tiposDoc.put("Cédula de Extranjería (CE)", 3);
            tiposDoc.put("Pasaporte (PP)",             4);
        }
        cmbTipoDocVisitante.getItems().setAll(tiposDoc.keySet());
    }

    private void configurarTabla() {
        colIdVisita.setCellValueFactory(new PropertyValueFactory<>("idVisita"));
        colResidente.setCellValueFactory(new PropertyValueFactory<>("idResidente"));
        colFechaRegistro.setCellValueFactory(new PropertyValueFactory<>("fechaRegistro"));
        colCantPersonas.setCellValueFactory(new PropertyValueFactory<>("cantidadPersonas"));
        colEstadoVisita.setCellValueFactory(new PropertyValueFactory<>("estado"));
        tablaVisitas.setItems(lista);
    }

    private void configurarTablaFrecuentes() {
        colNombreFrec.setCellValueFactory(new PropertyValueFactory<>("nombreVisitante"));
        colDocumentoFrec.setCellValueFactory(new PropertyValueFactory<>("documento"));
        colTotalVisitas.setCellValueFactory(new PropertyValueFactory<>("totalVisitas"));

        // Columna "Última visita" formateada como "dd/MM/yy HH:mm"
        colUltimaVisita.setCellValueFactory(new PropertyValueFactory<>("ultimaVisita"));
        colUltimaVisita.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : FMT_FECHA.format(item));
            }
        });

        tablaFrecuentes.setItems(listaFrecuentes);
    }

    // =========================================================
    // Carga de datos
    // =========================================================

    private void cargarResidentes() {
        try {
            List<Residente> residentes = residenteService.listarTodos();
            ObservableList<Residente> obs = FXCollections.observableArrayList(residentes);
            cmbResidente.setItems(obs);
            cmbResidenteFrecuente.setItems(obs);
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar residentes: " + e.getMessage());
        }
    }

    private void cargarVisitasActivas() {
        try {
            lista.clear();
            lista.addAll(visitaService.listarPendientesYActivas());
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar visitas: " + e.getMessage());
        }
    }

    private void cargarFrecuentes(Integer idResidente) {
        listaFrecuentes.clear();
        lblUltimoVehiculoFrec.setText("");   // limpiar referencia al cambiar residente
        if (idResidente == null) return;
        try {
            listaFrecuentes.addAll(frecuenteDAO.findByResidente(idResidente));
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar visitantes frecuentes: " + e.getMessage());
        }
    }

    // =========================================================
    // TAB 1 — Registrar visita nueva
    // =========================================================

    @FXML
    private void registrarVisita() {
        try {
            String tipoSelec = cmbTipoDocVisitante.getValue();
            if (tipoSelec == null) { mostrarAlerta("Advertencia", "Seleccione tipo de documento del visitante."); return; }
            Residente resAutorizante = cmbResidente.getValue();
            if (resAutorizante == null) { mostrarAlerta("Advertencia", "Seleccione el residente autorizante."); return; }

            Integer idTipoDoc = tiposDoc.get(tipoSelec);
            if (idTipoDoc == null) {
                mostrarAlerta("Error", "Tipo de documento no reconocido. Vuelva a seleccionarlo o reinicie la aplicación.");
                return;
            }
            String  numDoc    = txtDocVisitante.getText().trim();
            if (numDoc.isEmpty()) { mostrarAlerta("Advertencia", "Ingrese el número de documento del visitante."); return; }

            String nombres   = txtNombresVisitante.getText().trim();
            String apellidos = txtApellidosVisitante.getText().trim();
            String telefono  = txtTelefonoVisitante.getText().trim();
            String emailRaw  = ValidadorCampos.normalizarEmail(txtEmailVisitante.getText());
            if (nombres.isEmpty())   { mostrarAlerta("Advertencia", "Ingrese el nombre del visitante."); return; }
            if (apellidos.isEmpty()) { mostrarAlerta("Advertencia", "Ingrese los apellidos del visitante."); return; }
            if (!emailRaw.isEmpty() && !ValidadorCampos.validarEmail(emailRaw)) {
                mostrarAlerta("Advertencia", "El correo electrónico no tiene un formato válido."); return;
            }

            // Buscar visitante existente o crear nuevo
            Visitante visitante = visitanteDAO.findByDocumento(idTipoDoc, numDoc);
            if (visitante == null) {
                visitante = new Visitante();
                visitante.setIdTipoDoc(idTipoDoc);
                visitante.setNumeroDocumento(numDoc);
                visitante.setNombres(nombres);
                visitante.setApellidos(apellidos);
                visitante.setTelefono(telefono);
                visitante.setEmail(emailRaw.isEmpty() ? null : emailRaw);
                Integer idV = visitanteDAO.insert(visitante);
                visitante.setId(idV);
            } else if (pathDocPdf != null && !pathDocPdf.isEmpty()) {
                visitanteDAO.update(visitante);
            }

            // Obtener contrato activo del residente autorizante
            Optional<Integer> idContratoOpt = obtenerIdContratoActivo(resAutorizante);
            if (idContratoOpt.isEmpty()) return;

            // Construir objeto Visita
            Visita visita = new Visita();
            visita.setIdResidente(resAutorizante.getId());
            visita.setIdContratoRes(idContratoOpt.get());
            visita.setTiempoValidezMin(spnTiempoValidez.getValue());
            visita.setCantidadPersonas(spnCantPersonas.getValue());
            visita.setNotas(txtNotasVisita.getText().trim());

            // Vehículo opcional
            VehiculoVisita vehiculo = null;
            String placa = txtPlacaVehiculo.getText().trim();
            if (!placa.isEmpty() && cmbTipoVehiculo.getValue() != null) {
                vehiculo = new VehiculoVisita();
                vehiculo.setPlaca(placa);
                vehiculo.setTipo(cmbTipoVehiculo.getValue());
            }

            String codigoQR = visitaService.crearVisita(visita, visitante.getId(), vehiculo);

            mostrarAlerta("Éxito", "Visita registrada correctamente.\n"
                    + "El código QR ha sido generado y puede ser consultado\n"
                    + "por el residente desde su panel de acceso.");

            cargarVisitasActivas();
            limpiarFormulario();
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    @FXML
    private void cancelarVisita() {
        Visita sel = tablaVisitas.getSelectionModel().getSelectedItem();
        if (sel == null) { mostrarAlerta("Advertencia", "Seleccione una visita."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar cancelación");
        confirm.setContentText("¿Cancelar visita #" + sel.getIdVisita() + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                visitaService.cancelar(sel.getIdVisita());
                cargarVisitasActivas();
            } catch (Exception e) {
                mostrarAlerta("Error", e.getMessage());
            }
        }
    }

    @FXML
    private void limpiarFormulario() {
        txtNombresVisitante.clear();
        txtApellidosVisitante.clear();
        txtDocVisitante.clear();
        txtTelefonoVisitante.clear();
        txtEmailVisitante.clear();
        cmbTipoDocVisitante.setValue(null);
        cmbResidente.setValue(null);
        spnTiempoValidez.getValueFactory().setValue(30);
        spnCantPersonas.getValueFactory().setValue(1);
        txtNotasVisita.clear();
        txtPlacaVehiculo.clear();
        cmbTipoVehiculo.setValue(null);
        pathDocPdf = null;
        if (lblDocPdfSeleccionado != null)
            lblDocPdfSeleccionado.setText("Sin archivo");
    }

    /** Selecciona el PDF del documento de identificación del visitante. */
    @FXML
    private void seleccionarDocPdf() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar documento de identificación (PDF)");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.jpeg", "*.png")
        );
        java.io.File archivo = fc.showOpenDialog(
                lblDocPdfSeleccionado.getScene().getWindow());
        if (archivo != null) {
            pathDocPdf = archivo.getAbsolutePath();
            String nombre = archivo.getName();
            lblDocPdfSeleccionado.setText(nombre.length() > 25
                    ? nombre.substring(0, 22) + "..." : nombre);
        }
    }

    // =========================================================
    // TAB 2 — Visita rápida: frecuentes
    // =========================================================

    /** Pre-llena placa, tipo de vehículo y descripción con los datos de la última visita del frecuente. */
    private void preLlenarFormFrecuente(VisitanteFrecuente frec) {
        txtPlacaFrecuente.setText(frec.getUltimaPlaca() != null ? frec.getUltimaPlaca() : "");

        if (frec.getUltimoTipoVehiculo() != null) {
            try {
                cmbTipoVehiculoFrecuente.setValue(TipoVehiculo.valueOf(frec.getUltimoTipoVehiculo()));
            } catch (IllegalArgumentException e) {
                cmbTipoVehiculoFrecuente.setValue(null);
            }
        } else {
            cmbTipoVehiculoFrecuente.setValue(null);
        }

        txtDescripcionTipoFrecuente.setText(
                frec.getUltimaDescripcionTipo() != null ? frec.getUltimaDescripcionTipo() : "");

        // Mostrar referencia histórica en itálica para que el operador confirme si aplica
        if (frec.getUltimoTipoVehiculo() != null) {
            String ref = "Última vez: " + frec.getUltimoTipoVehiculo()
                    + (frec.getUltimaPlaca() != null ? " – " + frec.getUltimaPlaca() : "");
            lblUltimoVehiculoFrec.setText(ref);
        } else {
            lblUltimoVehiculoFrec.setText("Última vez: a pie (sin vehículo)");
        }
    }

    /** Limpia placa, tipo de vehículo y descripción en un clic (el visitante viene a pie hoy). */
    @FXML
    private void limpiarVehiculoFrecuente() {
        txtPlacaFrecuente.clear();
        cmbTipoVehiculoFrecuente.setValue(null);   // null = "(Sin vehículo — a pie)"
        txtDescripcionTipoFrecuente.clear();
    }

    @FXML
    private void liberarVisitaFrecuente() {
        VisitanteFrecuente frec = tablaFrecuentes.getSelectionModel().getSelectedItem();
        if (frec == null) {
            mostrarAlerta("Advertencia", "Seleccione un visitante frecuente de la tabla.");
            return;
        }
        Residente res = cmbResidenteFrecuente.getValue();
        if (res == null) {
            mostrarAlerta("Advertencia", "Seleccione el residente autorizante.");
            return;
        }

        try {
            // Obtener contrato activo del residente
            Optional<Integer> idContratoOpt = obtenerIdContratoActivo(res);
            if (idContratoOpt.isEmpty()) return;

            // Parámetros del formulario
            int    cantPersonas    = spnCantPersonasFrecuente.getValue();
            int    tiempoValidez   = spnTiempoValidezFrecuente.getValue();
            String placa           = txtPlacaFrecuente.getText().trim();
            TipoVehiculo tipoV     = cmbTipoVehiculoFrecuente.getValue();
            String tipoVehiculo    = tipoV != null ? tipoV.name() : null;
            String descripcion     = txtDescripcionTipoFrecuente.getText().trim();
            String notas           = txtNotasFrecuente.getText().trim();

            // Llamar al SP
            VisitanteFrecuenteDAO.LiberarVisitaResult resultado = frecuenteDAO.liberarVisita(
                    frec.getIdVisitante(),
                    idContratoOpt.get(),
                    res.getId(),
                    cantPersonas,
                    tiempoValidez,
                    tipoVehiculo,
                    placa.isEmpty()      ? null : placa,
                    descripcion.isEmpty() ? null : descripcion,
                    notas.isEmpty()       ? null : notas
            );

            // Actualizar estado compartido para botones de compartir
            ultimoCodigoQR = resultado.codigoQR;
            try {
                // Recuperar teléfono/email del visitante para los canales de compartir
                Visitante v = visitanteDAO.findById(frec.getIdVisitante());
                telefonoUltimoVisitante = v != null ? v.getTelefono() : null;
                emailUltimoVisitante    = v != null ? v.getEmail()    : null;
            } catch (Exception ignored) {
                telefonoUltimoVisitante = null;
                emailUltimoVisitante    = null;
            }

            // Mostrar QR en panel derecho del Tab 2
            lblCodigoQRFrecuente.setText("QR: " + resultado.codigoQR);
            mostrarImagenQR(resultado.codigoQR, imgQRFrecuente);

            mostrarAlerta("Éxito",
                    resultado.mensaje != null ? resultado.mensaje
                            : "QR generado para " + frec.getNombreVisitante());

            // Refrescar la tabla
            cargarFrecuentes(res.getId());

        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    @FXML
    private void ocultarFrecuente() {
        VisitanteFrecuente frec = tablaFrecuentes.getSelectionModel().getSelectedItem();
        if (frec == null) {
            mostrarAlerta("Advertencia", "Seleccione un visitante frecuente para ocultarlo.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Ocultar a " + frec.getNombreVisitante()
                + " de la lista de frecuentes de este residente?\n\n"
                + "Se reactivará automáticamente si vuelve a visitar.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                frecuenteDAO.ocultarFrecuente(frec.getIdFrecuente());
                cargarFrecuentes(cmbResidenteFrecuente.getValue() != null
                        ? cmbResidenteFrecuente.getValue().getId() : null);
            } catch (Exception e) {
                mostrarAlerta("Error", "No se pudo ocultar el frecuente: " + e.getMessage());
            }
        }
    }

    // =========================================================
    // Compartir QR (Tab 1 y Tab 2 comparten los mismos handlers)
    // =========================================================

    @FXML
    private void compartirPorCorreo() {
        if (ultimoCodigoQR == null) { mostrarAlerta("Advertencia", "Primero genere un QR."); return; }
        try {
            notificacionService.compartirPorCorreo(emailUltimoVisitante, ultimoCodigoQR);
        } catch (IllegalArgumentException e) {
            mostrarAlerta("Advertencia", e.getMessage());
        } catch (Exception e) {
            mostrarAlerta("Error", "No se pudo abrir el cliente de correo: " + e.getMessage());
        }
    }

    @FXML
    private void compartirPorTelegram() {
        if (ultimoCodigoQR == null) { mostrarAlerta("Advertencia", "Primero genere un QR."); return; }
        try {
            notificacionService.compartirPorTelegram(ultimoCodigoQR);
        } catch (Exception e) {
            mostrarAlerta("Error", "No se pudo abrir Telegram: " + e.getMessage());
        }
    }

    @FXML
    private void compartirPorSMS() {
        if (ultimoCodigoQR == null) { mostrarAlerta("Advertencia", "Primero genere un QR."); return; }
        String telefono = notificacionService.compartirPorSMS(telefonoUltimoVisitante, ultimoCodigoQR);
        mostrarAlerta("SMS — Código copiado",
                "El código QR fue copiado al portapapeles.\n\n"
              + "Pégalo en tu app de mensajería y envíalo al número:\n"
              + telefono);
    }

    // =========================================================
    // Parqueaderos visitantes (Tab 1 — solo lectura)
    // =========================================================

    private void configurarTablaParqueaderos() {
        colPrkVCodigo.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getCodigo()));
        colPrkVTipo.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getTipo() != null ? data.getValue().getTipo().name() : ""));
        colPrkVEstado.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getEstado() != null ? data.getValue().getEstado().name() : ""));
        tablaParqueaderosVisitantes.setItems(listaParqueaderosVisitantes);
    }

    private void cargarParqueaderosVisitantes() {
        try {
            listaParqueaderosVisitantes.setAll(parqueaderoDAO.findTodosVisitantes());
        } catch (Exception e) {
            // No interrumpir el flujo principal; la tabla simplemente quedará vacía
            System.err.println("Error al cargar parqueaderos visitantes: " + e.getMessage());
        }
    }

    // =========================================================
    // Utilidades privadas
    // =========================================================

    /**
     * Muestra el BitMatrix del QR como WritableImage en el ImageView indicado.
     * Se llama tanto desde registrarVisita() (→ imgQR) como desde liberarVisitaFrecuente() (→ imgQRFrecuente).
     */
    private void mostrarImagenQR(String codigoQR, ImageView destino) {
        try {
            BitMatrix matrix = generadorQR.crearMatrizQR(codigoQR);
            int ancho = matrix.getWidth();
            int alto  = matrix.getHeight();
            WritableImage imagen = new WritableImage(ancho, alto);
            PixelWriter pw = imagen.getPixelWriter();
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    pw.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            destino.setImage(imagen);
        } catch (WriterException e) {
            mostrarAlerta("Advertencia", "No se pudo renderizar la imagen QR: " + e.getMessage());
        }
    }

    /**
     * Obtiene el ID del contrato-residente activo para el residente indicado.
     * Si tiene más de uno, muestra un ChoiceDialog para que el usuario elija.
     * Devuelve Optional.empty() si no hay contratos o el usuario cancela.
     */
    private Optional<Integer> obtenerIdContratoActivo(Residente res) throws Exception {
        List<ContratoResidente> contratos = contratoResidenteDAO.findByResidente(res.getId());
        if (contratos.isEmpty()) {
            mostrarAlerta("Advertencia",
                    "El residente seleccionado no tiene un contrato activo.");
            return Optional.empty();
        }
        if (contratos.size() == 1) {
            return Optional.of(contratos.get(0).getIdContratoRes());
        }
        // Más de un contrato activo: el usuario elige
        ChoiceDialog<ContratoResidente> dialogo =
                new ChoiceDialog<>(contratos.get(0), contratos);
        dialogo.setTitle("Seleccionar contrato");
        dialogo.setHeaderText("El residente tiene más de un contrato activo.");
        dialogo.setContentText("Seleccione el contrato para esta visita:");
        Optional<ContratoResidente> eleccion = dialogo.showAndWait();
        return eleccion.map(ContratoResidente::getIdContratoRes);
    }

    private void mostrarAlerta(String titulo, String contenido) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
