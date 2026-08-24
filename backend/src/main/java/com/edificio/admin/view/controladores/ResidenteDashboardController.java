package com.edificio.admin.view.controladores;

import com.edificio.admin.config.SesionUsuario;
import com.edificio.admin.dao.*;
import com.edificio.admin.model.*;
import com.edificio.admin.model.enums.EstadoCuota;
import com.edificio.admin.model.enums.TipoVehiculo;
import com.edificio.admin.service.NotificacionService;
import com.edificio.admin.service.VisitaService;
import com.edificio.admin.util.GeneradorQR;
import com.edificio.admin.util.ValidadorCampos;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.*;

/**
 * Controlador del panel exclusivo para el rol RESIDENTE.
 *
 * Secciones (5 tabs en TabPane):
 *   0. Mi Perfil        — ver y editar nombres, teléfono, email
 *   1. Mi Apartamento   — info del apt. y contrato activo (solo lectura)
 *   2. Mis Cuotas       — tabla de cuotas con totales pendiente/pagado
 *   3. Mis Frecuentes   — FlowPane de tarjetas (activos/ocultos + QR rápido)
 *   4. Nueva Visita     — formulario de visita + QR inline + share buttons
 */
public class ResidenteDashboardController {

    // =========================================================
    // FXML — Cabecera
    // =========================================================

    @FXML private Label lblBienvenida;

    // =========================================================
    // FXML — Tab 0: Mi Perfil
    // =========================================================

    @FXML private TextField txtPerfilNombres;
    @FXML private TextField txtPerfilApellidos;
    @FXML private TextField txtPerfilTelefono;
    @FXML private TextField txtPerfilEmail;
    @FXML private TextField txtPerfilDocumento;
    @FXML private Label     lblPerfilMensaje;

    // =========================================================
    // FXML — Tab 1: Mi Apartamento
    // =========================================================

    @FXML private Label lblAptNumero;
    @FXML private Label lblAptPiso;
    @FXML private Label lblAptTipo;
    @FXML private Label lblAptArea;
    @FXML private Label lblAptEstado;
    @FXML private Label lblContId;
    @FXML private Label lblContInicio;
    @FXML private Label lblContFin;
    @FXML private Label lblContValor;
    @FXML private Label lblContEstado;
    @FXML private Label lblAptSinInfo;

    // =========================================================
    // FXML — Tab 2: Mis Cuotas
    // =========================================================

    @FXML private TableView<CuotaArriendo>            tablaCuotasResidente;
    @FXML private TableColumn<CuotaArriendo, Integer> colCuotaId;
    @FXML private TableColumn<CuotaArriendo, Integer> colCuotaAnio;
    @FXML private TableColumn<CuotaArriendo, Integer> colCuotaMes;
    @FXML private TableColumn<CuotaArriendo, String>  colCuotaLimite;
    @FXML private TableColumn<CuotaArriendo, String>  colCuotaValor;
    @FXML private TableColumn<CuotaArriendo, String>  colCuotaEstado;
    @FXML private Label lblCuotasTotalPendiente;
    @FXML private Label lblCuotasTotalPagado;

    // =========================================================
    // FXML — Tab 3: Mis Frecuentes
    // =========================================================

    @FXML private Label    lblTituloSeccion;
    @FXML private Label    lblSinFrecuentes;
    @FXML private FlowPane flowFrecuentes;
    @FXML private Button   btnVerOcultos;
    @FXML private Button   btnVolver;

    // =========================================================
    // FXML — Tab 4: Nueva Visita + QR
    // =========================================================

    @FXML private TextField              txtVisNombres;
    @FXML private TextField              txtVisApellidos;
    @FXML private ComboBox<String>       cmbVisTipoDoc;
    @FXML private TextField              txtVisDocumento;
    @FXML private Label                  lblDocPdfSeleccionado;
    @FXML private TextField              txtVisTelefono;
    @FXML private TextField              txtVisEmail;
    @FXML private Spinner<Integer>       spnVisValidez;
    @FXML private Spinner<Integer>       spnVisPersonas;
    @FXML private TextArea               txtVisNotas;
    @FXML private TextField              txtVisPlaca;
    @FXML private ComboBox<TipoVehiculo> cmbVisTipoVehiculo;
    @FXML private Label                  lblVisCodigoQR;
    @FXML private ImageView              imgVisQR;

    private String pathDocPdf;

    // =========================================================
    // DAOs / Servicios
    // =========================================================

    private final ResidenteDAO          residenteDAO         = new ResidenteDAO();
    private final ContratoResidenteDAO  contratoResidenteDAO = new ContratoResidenteDAO();
    private final ContratoDAO           contratoDAO          = new ContratoDAO();
    private final ApartamentoDAO        apartamentoDAO       = new ApartamentoDAO();
    private final CuotaArriendoDAO      cuotaDAO             = new CuotaArriendoDAO();
    private final VisitanteDAO          visitanteDAO         = new VisitanteDAO();
    private final VisitanteFrecuenteDAO frecuenteDAO         = new VisitanteFrecuenteDAO();
    private final VisitaService         visitaService        = new VisitaService();
    private final NotificacionService   notificacionService  = new NotificacionService();
    private final GeneradorQR           generadorQR          = new GeneradorQR();
    private final TipoDocumentoDAO      tipoDocDAO           = new TipoDocumentoDAO();

    // =========================================================
    // Estado de sesión
    // =========================================================

    private int       idResidente;
    private Residente residente;

    /** ID del primer contrato activo — preloaded in inicializar() for cuotas. */
    private Integer idContratoActivo = null;

    /** Tipos de documento: display string → id (loaded once in initialize). */
    private final Map<String, Integer> tiposDoc = new LinkedHashMap<>();

    // ---- Estado QR Tab 3 (frecuentes dialog share) ----
    private String ultimoCodigoQR          = null;
    private String telefonoUltimoVisitante = null;
    private String emailUltimoVisitante    = null;

    // ---- Estado QR Tab 4 (nueva visita inline share) ----
    private String visUltimoCodigoQR  = null;
    private String visEmailUltimo     = null;
    private String visTelefonoUltimo  = null;

    // =========================================================
    // FXML initialize — called automatically on FXML load
    // =========================================================

    @FXML
    private void initialize() {
        // ── Tab 0: Perfil — only telefono and email are editable ──────────────
        ValidadorCampos.soloTelefono(txtPerfilTelefono,  20);
        ValidadorCampos.soloEmail(txtPerfilEmail);

        // ── Tab 2: Cuotas — table cell factories ──────────────────────────
        configurarTablaCuotas();

        // ── Tab 4: Nueva Visita — input validators + controls ─────────────
        cargarTiposDocumento();

        ValidadorCampos.soloNombres(txtVisNombres,   30);
        ValidadorCampos.soloNombres(txtVisApellidos, 30);
        ValidadorCampos.aplicarFormatterDocumento(txtVisDocumento, null);
        cmbVisTipoDoc.valueProperty().addListener(
                (obs, oldV, newV) -> ValidadorCampos.aplicarFormatterDocumento(txtVisDocumento, newV));
        ValidadorCampos.soloTelefono(txtVisTelefono, 20);
        ValidadorCampos.soloEmail(txtVisEmail);
        ValidadorCampos.soloAlfanumerico(txtVisPlaca, 10);

        spnVisValidez.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 60, 30));
        spnVisPersonas.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1));

        cmbVisTipoVehiculo.getItems().add(null);
        cmbVisTipoVehiculo.getItems().addAll(TipoVehiculo.values());
        cmbVisTipoVehiculo.setConverter(new StringConverter<>() {
            @Override public String toString(TipoVehiculo t)   { return t == null ? "(Sin vehículo — a pie)" : t.name(); }
            @Override public TipoVehiculo fromString(String s) { return null; }
        });
    }

    // =========================================================
    // Entry point — called from LoginController after FXML load
    // =========================================================

    /**
     * Initializes the dashboard with the authenticated user.
     * Must be called immediately after the FXML is loaded.
     */
    public void inicializar(Usuario usuario) {
        this.idResidente = usuario.getIdResidente() != null ? usuario.getIdResidente() : 0;

        try {
            if (idResidente > 0) {
                residente = residenteDAO.findById(idResidente);
            }
        } catch (Exception e) {
            residente = null;
        }

        String nombre = (residente != null) ? residente.getNombreCompleto() : usuario.getUsername();
        lblBienvenida.setText("Bienvenido/a, " + nombre);

        precargarContratoActivo();
        cargarPerfil();
        cargarApartamento();
        cargarCuotas();
        cargarActivos();
    }

    // =========================================================
    // Active contract helper (shared by multiple tabs)
    // =========================================================

    /** Preloads the first active contract's id_contrato into {@code idContratoActivo}. */
    private void precargarContratoActivo() {
        try {
            List<ContratoResidente> contratos = contratoResidenteDAO.findByResidente(idResidente);
            idContratoActivo = contratos.isEmpty() ? null : contratos.get(0).getIdContrato();
        } catch (Exception e) {
            idContratoActivo = null;
        }
    }

    /**
     * Returns the id_contrato_res (NOT id_contrato) of the active contract.
     * Shows a ChoiceDialog if the resident has more than one active contract.
     */
    private Optional<Integer> obtenerIdContratoResActivo() throws Exception {
        List<ContratoResidente> contratos = contratoResidenteDAO.findByResidente(idResidente);
        if (contratos.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin contrato activo",
                    "No tienes un contrato activo asociado. Contacta al administrador.");
            return Optional.empty();
        }
        if (contratos.size() == 1) {
            return Optional.of(contratos.get(0).getIdContratoRes());
        }
        ChoiceDialog<ContratoResidente> dlg =
                new ChoiceDialog<>(contratos.get(0), contratos);
        dlg.setTitle("Seleccionar contrato");
        dlg.setHeaderText("Tienes más de un contrato activo.");
        dlg.setContentText("Selecciona el contrato para esta visita:");
        return dlg.showAndWait().map(ContratoResidente::getIdContratoRes);
    }

    // =========================================================
    // TAB 0 — Mi Perfil
    // =========================================================

    /** Populates the profile form with the loaded resident's data. */
    @FXML
    public void cargarPerfil() {
        lblPerfilMensaje.setText("");
        if (residente == null) return;
        txtPerfilNombres.setText(residente.getNombres()         != null ? residente.getNombres()         : "");
        txtPerfilApellidos.setText(residente.getApellidos()     != null ? residente.getApellidos()       : "");
        txtPerfilTelefono.setText(residente.getTelefono()       != null ? residente.getTelefono()        : "");
        txtPerfilEmail.setText(residente.getEmail()             != null ? residente.getEmail()           : "");
        txtPerfilDocumento.setText(residente.getNumeroDocumento() != null ? residente.getNumeroDocumento() : "");
    }

    /** Validates and persists changes to teléfono and email only.
     *  Nombres, apellidos and documento are display-only and cannot be edited here. */
    @FXML
    private void guardarPerfil() {
        if (residente == null) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No hay datos de residente cargados.");
            return;
        }

        String telefono  = txtPerfilTelefono.getText().trim();
        String email     = ValidadorCampos.normalizarEmail(txtPerfilEmail.getText());

        if (!email.isEmpty() && !ValidadorCampos.validarEmail(email)) {
            mostrarAlerta(Alert.AlertType.WARNING, "Validación", "El correo no tiene un formato válido.");
            return;
        }

        residente.setTelefono(telefono.isEmpty() ? null : telefono);
        residente.setEmail(email.isEmpty() ? null : email);

        try {
            residenteDAO.update(residente);
            lblPerfilMensaje.setText("Datos guardados correctamente.");
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error al guardar", e.getMessage());
        }
    }

    // =========================================================
    // TAB 1 — Mi Apartamento
    // =========================================================

    private void cargarApartamento() {
        setVisible(lblAptSinInfo, false);
        try {
            List<ContratoResidente> contratos = contratoResidenteDAO.findByResidente(idResidente);
            if (contratos.isEmpty()) {
                setVisible(lblAptSinInfo, true);
                return;
            }

            ContratoResidente cr = contratos.get(0);
            Contrato contrato = contratoDAO.findById(cr.getIdContrato());
            if (contrato == null) {
                setVisible(lblAptSinInfo, true);
                return;
            }

            Apartamento apt = apartamentoDAO.findById(contrato.getIdApartamento());
            if (apt != null) {
                lblAptNumero.setText(apt.getNumero()  != null ? apt.getNumero()                          : "-");
                lblAptPiso.setText(String.valueOf(apt.getPiso()));
                lblAptTipo.setText(apt.getTipo()      != null ? apt.getTipo()                            : "-");
                lblAptArea.setText(apt.getAreaM2()    != null ? apt.getAreaM2().toPlainString() + " m²"  : "-");
                lblAptEstado.setText(apt.getEstado()  != null ? apt.getEstado().name()                   : "-");
            }

            lblContId.setText(String.valueOf(contrato.getIdContrato()));
            lblContInicio.setText(contrato.getFechaInicio() != null
                    ? contrato.getFechaInicio().toString() : "-");
            lblContFin.setText(contrato.getFechaFin() != null
                    ? contrato.getFechaFin().toString() : "Indefinido");
            lblContValor.setText(contrato.getValorMensual() != null
                    ? formatMoneda(contrato.getValorMensual()) : "-");
            lblContEstado.setText(contrato.getEstado() != null
                    ? contrato.getEstado().name() : "-");

        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error",
                    "No se pudo cargar la información del apartamento:\n" + e.getMessage());
        }
    }

    // =========================================================
    // TAB 2 — Mis Cuotas
    // =========================================================

    private void configurarTablaCuotas() {
        colCuotaId.setCellValueFactory(new PropertyValueFactory<>("idCuota"));
        colCuotaAnio.setCellValueFactory(new PropertyValueFactory<>("anio"));
        colCuotaMes.setCellValueFactory(new PropertyValueFactory<>("mes"));

        colCuotaLimite.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getFechaLimite() != null
                                ? data.getValue().getFechaLimite().toString() : "-"));

        colCuotaValor.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getValorTotal() != null
                                ? formatMoneda(data.getValue().getValorTotal()) : "-"));

        colCuotaEstado.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getEstado() != null
                                ? data.getValue().getEstado().name() : "-"));
    }

    private void cargarCuotas() {
        if (idContratoActivo == null) {
            lblCuotasTotalPendiente.setText("$ 0");
            lblCuotasTotalPagado.setText("$ 0");
            return;
        }
        try {
            List<CuotaArriendo> cuotas = cuotaDAO.findByContrato(idContratoActivo);
            tablaCuotasResidente.setItems(FXCollections.observableArrayList(cuotas));

            BigDecimal pendiente = BigDecimal.ZERO;
            BigDecimal pagado    = BigDecimal.ZERO;
            for (CuotaArriendo c : cuotas) {
                if (c.getValorTotal() == null) continue;
                EstadoCuota est = c.getEstado();
                if (est == EstadoCuota.PAGADA) {
                    pagado = pagado.add(c.getValorTotal());
                } else if (est == EstadoCuota.PENDIENTE
                        || est == EstadoCuota.VENCIDA
                        || est == EstadoCuota.EN_MORA) {
                    pendiente = pendiente.add(c.getValorTotal());
                }
            }

            lblCuotasTotalPendiente.setText(formatMoneda(pendiente));
            lblCuotasTotalPagado.setText(formatMoneda(pagado));

        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error",
                    "No se pudieron cargar las cuotas:\n" + e.getMessage());
        }
    }

    // =========================================================
    // TAB 3 — Mis Frecuentes
    // =========================================================

    /** Switches to the active-visitors view (default). */
    @FXML
    public void mostrarActivos() {
        lblTituloSeccion.setText("Mis Visitantes Frecuentes");
        setVisible(btnVerOcultos, true);
        setVisible(btnVolver, false);
        cargarActivos();
    }

    /** Switches to the hidden-visitors view. */
    @FXML
    private void mostrarOcultos() {
        lblTituloSeccion.setText("Visitantes Ocultos");
        setVisible(btnVerOcultos, false);
        setVisible(btnVolver, true);
        cargarOcultos();
    }

    private void cargarActivos() {
        flowFrecuentes.getChildren().clear();
        try {
            List<VisitanteFrecuente> lista = frecuenteDAO.findByResidente(idResidente);
            if (lista.isEmpty()) {
                setVisible(lblSinFrecuentes, true);
            } else {
                setVisible(lblSinFrecuentes, false);
                lista.forEach(frec -> flowFrecuentes.getChildren().add(crearTarjetaActivo(frec)));
            }
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error",
                    "No se pudieron cargar los visitantes frecuentes:\n" + e.getMessage());
        }
    }

    private void cargarOcultos() {
        flowFrecuentes.getChildren().clear();
        try {
            List<VisitanteFrecuente> lista = frecuenteDAO.findOcultosByResidente(idResidente);
            if (lista.isEmpty()) {
                setVisible(lblSinFrecuentes, true);
                lblSinFrecuentes.setText("No tienes visitantes ocultos.");
            } else {
                setVisible(lblSinFrecuentes, false);
                lista.forEach(frec -> flowFrecuentes.getChildren().add(crearTarjetaOculto(frec)));
            }
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error",
                    "No se pudieron cargar los visitantes ocultos:\n" + e.getMessage());
        }
    }

    // ---- Card builders ----

    private VBox crearTarjetaActivo(VisitanteFrecuente frec) {
        VBox card = nuevaTarjeta(false);

        Label lblNombre = new Label(frec.getNombreVisitante());
        lblNombre.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        lblNombre.setWrapText(true);

        Label lblDoc = new Label("Doc: " + frec.getDocumento());
        lblDoc.setStyle("-fx-font-size: 12px;");

        Label lblVisitas = new Label("Visitas: " + frec.getTotalVisitas());
        lblVisitas.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button btnQR = new Button("Generar QR Rápido");
        btnQR.setMaxWidth(Double.MAX_VALUE);
        btnQR.setStyle("-fx-background-color: #3a7bd5; -fx-text-fill: white; "
                     + "-fx-font-weight: bold; -fx-cursor: hand;");
        btnQR.setOnAction(e -> abrirDialogoQR(frec));

        Button btnOcultar = new Button("Ocultar");
        btnOcultar.setMaxWidth(Double.MAX_VALUE);
        btnOcultar.setStyle("-fx-background-color: #cccccc; -fx-text-fill: #333333; -fx-cursor: hand;");
        btnOcultar.setOnAction(e -> ocultarFrecuente(frec));

        card.getChildren().addAll(lblNombre, lblDoc, lblVisitas, spacer, btnQR, btnOcultar);
        return card;
    }

    private VBox crearTarjetaOculto(VisitanteFrecuente frec) {
        VBox card = nuevaTarjeta(true);

        Label lblNombre = new Label(frec.getNombreVisitante());
        lblNombre.setStyle("-fx-font-size: 13px; -fx-text-fill: #999999;");
        lblNombre.setWrapText(true);

        Label lblDoc = new Label("Doc: " + frec.getDocumento());
        lblDoc.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaaaaa;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button btnDesbloquear = new Button("Desbloquear");
        btnDesbloquear.setMaxWidth(Double.MAX_VALUE);
        btnDesbloquear.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; "
                              + "-fx-font-weight: bold; -fx-cursor: hand;");
        btnDesbloquear.setOnAction(e -> desbloquearFrecuente(frec));

        card.getChildren().addAll(lblNombre, lblDoc, spacer, btnDesbloquear);
        return card;
    }

    private VBox nuevaTarjeta(boolean apagada) {
        VBox card = new VBox(8);
        card.setPrefWidth(185);
        card.setPrefHeight(160);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.TOP_LEFT);
        String borde    = apagada ? "#cccccc" : "#3a7bd5";
        double opacidad = apagada ? 0.70 : 1.0;
        card.setStyle("-fx-border-color: " + borde + "; "
                    + "-fx-border-radius: 8px; "
                    + "-fx-background-radius: 8px; "
                    + "-fx-background-color: white; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 6, 0, 0, 2);");
        card.setOpacity(opacidad);
        return card;
    }

    // ---- QR dialog for frequent visitors ----

    private void abrirDialogoQR(VisitanteFrecuente frec) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(130);
        col0.setHgrow(Priority.NEVER);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);

        Spinner<Integer> spnPersonas = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1));
        spnPersonas.setPrefWidth(120);

        Spinner<Integer> spnValidez = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 60, 30));
        spnValidez.setPrefWidth(120);

        TextField txtPlaca = new TextField();
        txtPlaca.setPromptText("Vacío = a pie");

        ComboBox<TipoVehiculo> cmbTipo = new ComboBox<>();
        cmbTipo.getItems().add(null);
        cmbTipo.getItems().addAll(TipoVehiculo.values());
        cmbTipo.setValue(null);
        cmbTipo.setConverter(new StringConverter<>() {
            @Override public String toString(TipoVehiculo t)   { return t == null ? "(Sin vehículo — a pie)" : t.name(); }
            @Override public TipoVehiculo fromString(String s) { return null; }
        });
        cmbTipo.setPrefWidth(220);

        TextField txtDescripcion = new TextField();
        txtDescripcion.setPromptText("Solo si tipo = OTRO");

        TextArea txtNotas = new TextArea();
        txtNotas.setPrefRowCount(2);
        txtNotas.setWrapText(true);

        Button btnAPie = new Button("A pie");
        btnAPie.setOnAction(e -> { txtPlaca.clear(); cmbTipo.setValue(null); txtDescripcion.clear(); });

        // Pre-fill from last visit
        if (frec.getUltimoTipoVehiculo() != null) {
            try { cmbTipo.setValue(TipoVehiculo.valueOf(frec.getUltimoTipoVehiculo())); }
            catch (IllegalArgumentException ignored) {}
        }
        if (frec.getUltimaPlaca() != null)          txtPlaca.setText(frec.getUltimaPlaca());
        if (frec.getUltimaDescripcionTipo() != null) txtDescripcion.setText(frec.getUltimaDescripcionTipo());

        String ref = frec.getUltimoTipoVehiculo() != null
                ? "Última vez: " + frec.getUltimoTipoVehiculo()
                  + (frec.getUltimaPlaca() != null ? " – " + frec.getUltimaPlaca() : "")
                : "Última vez: a pie";
        Label lblRef = new Label(ref);
        lblRef.setStyle("-fx-text-fill: #777777; -fx-font-style: italic; -fx-font-size: 11px;");

        int r = 0;
        grid.add(new Label("Personas:"),      0, r); grid.add(spnPersonas,    1, r++);
        grid.add(new Label("Validez (min):"), 0, r); grid.add(spnValidez,     1, r++);
        grid.add(new Label("Modo HOY:"),      0, r); grid.add(lblRef,          1, r++);
        grid.add(new Label("Placa:"),         0, r);
        HBox placaBox = new HBox(6, txtPlaca, btnAPie);
        HBox.setHgrow(txtPlaca, Priority.ALWAYS);
        grid.add(placaBox,                    1, r++);
        grid.add(new Label("Tipo Vehículo:"), 0, r); grid.add(cmbTipo,        1, r++);
        grid.add(new Label("Descripción:"),   0, r); grid.add(txtDescripcion,  1, r++);
        grid.add(new Label("Notas:"),         0, r); grid.add(txtNotas,        1, r);
        GridPane.setHgrow(txtNotas, Priority.ALWAYS);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Generar acceso para " + frec.getNombreVisitante());
        dialogo.setHeaderText(null);
        dialogo.getDialogPane().setContent(grid);
        dialogo.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Generar QR", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);
        dialogo.getDialogPane().setPrefWidth(460);

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) return;

        try {
            Optional<Integer> idContratoOpt = obtenerIdContratoResActivo();
            if (idContratoOpt.isEmpty()) return;

            String tipoVehiculo = cmbTipo.getValue() != null ? cmbTipo.getValue().name() : null;
            String placa        = txtPlaca.getText().trim();
            String descripcion  = txtDescripcion.getText().trim();
            String notas        = txtNotas.getText().trim();

            VisitanteFrecuenteDAO.LiberarVisitaResult qrResult = frecuenteDAO.liberarVisita(
                    frec.getIdVisitante(),
                    idContratoOpt.get(),
                    idResidente,
                    spnPersonas.getValue(),
                    spnValidez.getValue(),
                    tipoVehiculo,
                    placa.isEmpty()       ? null : placa,
                    descripcion.isEmpty() ? null : descripcion,
                    notas.isEmpty()       ? null : notas
            );

            ultimoCodigoQR = qrResult.codigoQR;
            try {
                Visitante v = visitanteDAO.findById(frec.getIdVisitante());
                telefonoUltimoVisitante = v != null ? v.getTelefono() : null;
                emailUltimoVisitante    = v != null ? v.getEmail()    : null;
            } catch (Exception ignored) {
                telefonoUltimoVisitante = null;
                emailUltimoVisitante    = null;
            }

            mostrarDialogoQR(frec.getNombreVisitante(), qrResult.codigoQR,
                    qrResult.mensaje != null ? qrResult.mensaje : "QR generado correctamente.");
            cargarActivos();

        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error al generar QR", e.getMessage());
        }
    }

    private void mostrarDialogoQR(String nombreVisitante, String codigoQR, String mensaje) {
        ImageView imgQR = new ImageView();
        imgQR.setFitWidth(220);
        imgQR.setFitHeight(220);
        imgQR.setPreserveRatio(true);
        try {
            BitMatrix matrix = generadorQR.crearMatrizQR(codigoQR);
            WritableImage imagen = new WritableImage(matrix.getWidth(), matrix.getHeight());
            PixelWriter pw = imagen.getPixelWriter();
            for (int y = 0; y < matrix.getHeight(); y++)
                for (int x = 0; x < matrix.getWidth(); x++)
                    pw.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            imgQR.setImage(imagen);
        } catch (WriterException ignored) { }

        Label lblMensaje = new Label(mensaje);
        lblMensaje.setWrapText(true);
        lblMensaje.setStyle("-fx-font-size: 12px;");

        Label lblCodigo = new Label("Código: " + codigoQR);
        lblCodigo.setStyle("-fx-font-size: 11px; -fx-text-fill: #555555;");
        lblCodigo.setWrapText(true);

        Button btnCorreo = new Button("Correo");
        btnCorreo.setOnAction(e -> {
            try { notificacionService.compartirPorCorreo(emailUltimoVisitante, ultimoCodigoQR); }
            catch (IllegalArgumentException ex) { mostrarAlerta(Alert.AlertType.WARNING, "Advertencia", ex.getMessage()); }
            catch (Exception ex)               { mostrarAlerta(Alert.AlertType.ERROR,   "Error",       ex.getMessage()); }
        });

        Button btnTelegram = new Button("Telegram");
        btnTelegram.setOnAction(e -> {
            try { notificacionService.compartirPorTelegram(ultimoCodigoQR); }
            catch (Exception ex) { mostrarAlerta(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
        });

        Button btnSMS = new Button("SMS");
        btnSMS.setOnAction(e -> {
            String tel = notificacionService.compartirPorSMS(telefonoUltimoVisitante, ultimoCodigoQR);
            mostrarAlerta(Alert.AlertType.INFORMATION, "SMS — Código copiado",
                    "El código fue copiado al portapapeles.\nEnvíalo al número: " + tel);
        });

        HBox shareRow = new HBox(10, btnCorreo, btnTelegram, btnSMS);
        shareRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(btnCorreo, Priority.ALWAYS);
        HBox.setHgrow(btnTelegram, Priority.ALWAYS);
        HBox.setHgrow(btnSMS, Priority.ALWAYS);
        btnCorreo.setMaxWidth(Double.MAX_VALUE);
        btnTelegram.setMaxWidth(Double.MAX_VALUE);
        btnSMS.setMaxWidth(Double.MAX_VALUE);

        VBox contenido = new VBox(10, imgQR, lblMensaje, lblCodigo, shareRow);
        contenido.setAlignment(Pos.CENTER);
        contenido.setPadding(new Insets(15));

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("QR para " + nombreVisitante);
        dlg.setHeaderText(null);
        dlg.getDialogPane().setContent(contenido);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private void ocultarFrecuente(VisitanteFrecuente frec) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Ocultar visitante");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Ocultar a " + frec.getNombreVisitante()
                + " de tu lista de frecuentes?\n\n"
                + "Podrás volver a activarlo desde 'Ver Ocultos'.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                frecuenteDAO.ocultarFrecuente(frec.getIdFrecuente());
                cargarActivos();
            } catch (Exception e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudo ocultar: " + e.getMessage());
            }
        }
    }

    private void desbloquearFrecuente(VisitanteFrecuente frec) {
        try {
            frecuenteDAO.reactivarFrecuente(frec.getIdFrecuente());
            mostrarActivos();
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudo desbloquear: " + e.getMessage());
        }
    }

    // =========================================================
    // TAB 4 — Nueva Visita + QR
    // =========================================================

    private void cargarTiposDocumento() {
        try {
            tipoDocDAO.findActivos().forEach(t ->
                    tiposDoc.put(t.getDescripcion() + " (" + t.getCodigo() + ")", t.getIdTipoDoc()));
        } catch (Exception e) {
            // Fallback — matches seed order (CC=1, TI=2, CE=3, PP=4)
            tiposDoc.put("Cédula de Ciudadanía (CC)", 1);
            tiposDoc.put("Tarjeta de Identidad (TI)", 2);
            tiposDoc.put("Cédula de Extranjería (CE)", 3);
            tiposDoc.put("Pasaporte (PP)",             4);
        }
        cmbVisTipoDoc.getItems().setAll(tiposDoc.keySet());
    }

    @FXML
    private void registrarVisitaResidente() {
        try {
            // ── Validate visitor fields ────────────────────────────────
            String tipoSelec = cmbVisTipoDoc.getValue();
            if (tipoSelec == null) {
                mostrarAlerta(Alert.AlertType.WARNING, "Validación",
                        "Seleccione el tipo de documento del visitante."); return;
            }
            Integer idTipoDoc = tiposDoc.get(tipoSelec);
            if (idTipoDoc == null) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error",
                        "Tipo de documento no reconocido. Vuelva a seleccionarlo o reinicie la aplicación.");
                return;
            }
            String  numDoc    = txtVisDocumento.getText().trim();
            if (numDoc.isEmpty()) {
                mostrarAlerta(Alert.AlertType.WARNING, "Validación",
                        "Ingrese el número de documento del visitante."); return;
            }
            String nombres   = ValidadorCampos.normalizarNombre(txtVisNombres.getText());
            String apellidos = ValidadorCampos.normalizarNombre(txtVisApellidos.getText());
            String telefono  = txtVisTelefono.getText().trim();
            String emailRaw  = ValidadorCampos.normalizarEmail(txtVisEmail.getText());

            if (nombres.isEmpty()) {
                mostrarAlerta(Alert.AlertType.WARNING, "Validación",
                        "Ingrese el nombre del visitante."); return;
            }
            if (apellidos.isEmpty()) {
                mostrarAlerta(Alert.AlertType.WARNING, "Validación",
                        "Ingrese los apellidos del visitante."); return;
            }
            if (!emailRaw.isEmpty() && !ValidadorCampos.validarEmail(emailRaw)) {
                mostrarAlerta(Alert.AlertType.WARNING, "Validación",
                        "El correo no tiene un formato válido."); return;
            }

            // ── Find or create visitor ─────────────────────────────────
            Visitante visitante = visitanteDAO.findByDocumento(idTipoDoc, numDoc);
            if (visitante == null) {
                visitante = new Visitante();
                visitante.setIdTipoDoc(idTipoDoc);
                visitante.setNumeroDocumento(numDoc);
                visitante.setNombres(nombres);
                visitante.setApellidos(apellidos);
                visitante.setTelefono(telefono.isEmpty() ? null : telefono);
                visitante.setEmail(emailRaw.isEmpty() ? null : emailRaw);
                Integer idV = visitanteDAO.insert(visitante);
                visitante.setId(idV);
            } else if (pathDocPdf != null && !pathDocPdf.isEmpty()) {
                visitanteDAO.update(visitante);
            }

            // ── Get active contract (id_contrato_res) ──────────────────
            Optional<Integer> idContratoOpt = obtenerIdContratoResActivo();
            if (idContratoOpt.isEmpty()) return;

            // ── Build Visita ───────────────────────────────────────────
            Visita visita = new Visita();
            visita.setIdResidente(idResidente);
            visita.setIdContratoRes(idContratoOpt.get());
            visita.setTiempoValidezMin(spnVisValidez.getValue());
            visita.setCantidadPersonas(spnVisPersonas.getValue());
            visita.setNotas(txtVisNotas.getText().trim());

            // ── Optional vehicle ───────────────────────────────────────
            VehiculoVisita vehiculo = null;
            String placa = txtVisPlaca.getText().trim();
            if (!placa.isEmpty() && cmbVisTipoVehiculo.getValue() != null) {
                vehiculo = new VehiculoVisita();
                vehiculo.setPlaca(placa);
                vehiculo.setTipo(cmbVisTipoVehiculo.getValue());
            }

            // ── Persist + get QR code ──────────────────────────────────
            String codigoQR = visitaService.crearVisita(visita, visitante.getId(), vehiculo);

            // Save share state
            visUltimoCodigoQR = codigoQR;
            visEmailUltimo    = emailRaw.isEmpty()  ? null : emailRaw;
            visTelefonoUltimo = telefono.isEmpty()  ? null : telefono;

            // Show QR inline
            lblVisCodigoQR.setText("Código: " + codigoQR);
            renderQR(codigoQR);

        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error al registrar visita", e.getMessage());
        }
    }

    /** Renders a QR code string into the {@code imgVisQR} ImageView. */
    private void renderQR(String codigoQR) {
        try {
            BitMatrix matrix = generadorQR.crearMatrizQR(codigoQR);
            WritableImage imagen = new WritableImage(matrix.getWidth(), matrix.getHeight());
            PixelWriter pw = imagen.getPixelWriter();
            for (int y = 0; y < matrix.getHeight(); y++)
                for (int x = 0; x < matrix.getWidth(); x++)
                    pw.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            imgVisQR.setImage(imagen);
        } catch (WriterException e) {
            imgVisQR.setImage(null);
        }
    }

    /** Clears the Nueva Visita form and resets the QR panel. */
    @FXML
    private void limpiarFormVisita() {
        txtVisNombres.clear();
        txtVisApellidos.clear();
        txtVisDocumento.clear();
        txtVisTelefono.clear();
        txtVisEmail.clear();
        cmbVisTipoDoc.setValue(null);
        spnVisValidez.getValueFactory().setValue(30);
        spnVisPersonas.getValueFactory().setValue(1);
        txtVisNotas.clear();
        txtVisPlaca.clear();
        cmbVisTipoVehiculo.setValue(null);
        lblVisCodigoQR.setText("El QR aparecerá aquí al registrar la visita.");
        imgVisQR.setImage(null);
        visUltimoCodigoQR = null;
        visEmailUltimo    = null;
        visTelefonoUltimo = null;
        pathDocPdf = null;
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

    @FXML
    private void visCompartirCorreo() {
        if (visUltimoCodigoQR == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin QR",
                    "Primero registra una visita para generar un QR."); return;
        }
        try {
            notificacionService.compartirPorCorreo(visEmailUltimo, visUltimoCodigoQR);
        } catch (IllegalArgumentException ex) {
            mostrarAlerta(Alert.AlertType.WARNING, "Advertencia", ex.getMessage());
        } catch (Exception ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", ex.getMessage());
        }
    }

    @FXML
    private void visCompartirTelegram() {
        if (visUltimoCodigoQR == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin QR",
                    "Primero registra una visita para generar un QR."); return;
        }
        try {
            notificacionService.compartirPorTelegram(visUltimoCodigoQR);
        } catch (Exception ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", ex.getMessage());
        }
    }

    @FXML
    private void visCompartirSMS() {
        if (visUltimoCodigoQR == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin QR",
                    "Primero registra una visita para generar un QR."); return;
        }
        String tel = notificacionService.compartirPorSMS(visTelefonoUltimo, visUltimoCodigoQR);
        mostrarAlerta(Alert.AlertType.INFORMATION, "SMS — Código copiado",
                "El código fue copiado al portapapeles.\nEnvíalo al número: " + tel);
    }

    // =========================================================
    // Cerrar Sesión
    // =========================================================

    @FXML
    private void cerrarSesion() {
        try {
            SesionUsuario.cerrarSesion();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/edificio/admin/view/vistas/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) flowFrecuentes.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Login - Sistema de Administración Residencial");
            stage.show();
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudo cerrar sesión: " + e.getMessage());
        }
    }

    // =========================================================
    // Utilities
    // =========================================================

    /** Sets both visible and managed simultaneously to free layout space. */
    private void setVisible(javafx.scene.Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }

    /** Formats a BigDecimal as Colombian peso currency string. */
    private String formatMoneda(BigDecimal valor) {
        if (valor == null) return "$ 0";
        return String.format("$ %,.0f", valor.doubleValue());
    }
}
