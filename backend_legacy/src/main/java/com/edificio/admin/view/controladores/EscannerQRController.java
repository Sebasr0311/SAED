package com.edificio.admin.view.controladores;

import com.edificio.admin.config.SesionUsuario;
import com.edificio.admin.dao.ConexionBD;
import com.edificio.admin.dao.ParqueaderoDAO;
import com.edificio.admin.dao.QRAccesoDAO;
import com.edificio.admin.dao.RegistroAccesoDAO;
import com.edificio.admin.model.Parqueadero;
import com.edificio.admin.model.QRAcceso;
import com.edificio.admin.model.RegistroAcceso;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controlador para la pantalla de escáner QR.
 *
 * Tab 1 — Validar Entrada:
 *   1. El portero pega el código QR en txtCodigoQR y pulsa "Validar".
 *   2. Se llama a SP_VALIDAR_QR(codigo, id_vigilante, p_valido OUT, p_mensaje OUT, p_cursor OUT).
 *   3. Si es válido el SP inserta en REGISTROS_ACCESO (hora_entrada = now) y marca el QR como usado.
 *   4. El resultado y los datos del cursor se muestran en pantalla.
 *
 * Tab 2 — Registrar Salida:
 *   1. Muestra la tabla de visitas aún dentro del edificio (hora_salida IS NULL).
 *   2. El portero selecciona una fila y pulsa "Registrar Salida".
 *   3. Se actualiza hora_salida = SYSTIMESTAMP en REGISTROS_ACCESO.
 *   4. TRG_ACCESO_SALIDA se dispara y cambia VISITAS.estado → FINALIZADA,
 *      libera parqueaderos y registra hora_salida en VEHICULOS_VISITA.
 */
public class EscannerQRController implements ControladorVista {

    // ── Tab 1: Validar Entrada ────────────────────────────────────────────────
    @FXML private TextField     txtCodigoQR;
    @FXML private Label         lblResultado;
    @FXML private TextArea      taInfoAcceso;
    @FXML private DatePicker    dpFiltroFechaQR;

    @FXML private TableView<QRAcceso>               tablaQR;
    @FXML private TableColumn<QRAcceso, Integer>    colIdQR;
    @FXML private TableColumn<QRAcceso, Integer>    colIdVisita;
    @FXML private TableColumn<QRAcceso, String>     colCodigo;
    @FXML private TableColumn<QRAcceso, LocalDateTime> colExpiracion;
    @FXML private TableColumn<QRAcceso, String>     colUsado;
    @FXML private TableColumn<QRAcceso, LocalDateTime> colFechaUso;

    // ── Tab 2: Registrar Salida ───────────────────────────────────────────────
    @FXML private TableView<RegistroAcceso>               tablaAccesos;
    @FXML private TableColumn<RegistroAcceso, Integer>    colAccesoId;
    @FXML private TableColumn<RegistroAcceso, Integer>    colAccesoVisita;
    @FXML private TableColumn<RegistroAcceso, String>     colAccesoResidente;
    @FXML private TableColumn<RegistroAcceso, LocalDateTime> colAccesoHoraEntrada;
    @FXML private TextField                               txtObservacionesSalida;

    // ── Tab 3: Parqueaderos ───────────────────────────────────────────────────
    @FXML private TableView<Parqueadero>           tablaParqueaderosPortero;
    @FXML private TableColumn<Parqueadero, String> colPrkCodigo;
    @FXML private TableColumn<Parqueadero, String> colPrkTipo;
    @FXML private TableColumn<Parqueadero, String> colPrkVisitante;
    @FXML private TableColumn<Parqueadero, String> colPrkApartamento;
    @FXML private TableColumn<Parqueadero, String> colPrkPropietario;
    @FXML private TableColumn<Parqueadero, String> colPrkEstado;
    @FXML private ComboBox<String>                 cmbPrkFiltroEstado;
    @FXML private ComboBox<String>                 cmbPrkFiltroTipo;
    @FXML private ComboBox<String>                 cmbPrkFiltroClasif;

    // ── DAOs ──────────────────────────────────────────────────────────────────
    private final QRAccesoDAO       qrAccesoDAO       = new QRAccesoDAO();
    private final RegistroAccesoDAO registroAccesoDAO = new RegistroAccesoDAO();
    private final ParqueaderoDAO    parqueaderoDAO    = new ParqueaderoDAO();

    // ── Listas observables ────────────────────────────────────────────────────
    private final ObservableList<QRAcceso>       listaQR             = FXCollections.observableArrayList();
    private final ObservableList<RegistroAcceso> listaAccesos        = FXCollections.observableArrayList();
    private final ObservableList<Parqueadero>    listaParqueaderos   = FXCollections.observableArrayList();

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        configurarTablaQR();
        configurarTablaAccesos();
        configurarTablaParqueaderos();

        // Inicializar filtros de parqueadero
        cmbPrkFiltroEstado.getItems().addAll("", "DISPONIBLE", "OCUPADO", "EN_MANTENIMIENTO");
        cmbPrkFiltroEstado.setValue("");
        cmbPrkFiltroTipo.getItems().addAll("", "VEHICULO", "MOTO", "BICICLETA");
        cmbPrkFiltroTipo.setValue("");
        cmbPrkFiltroClasif.getItems().addAll("", "Propietario", "Visitante");
        cmbPrkFiltroClasif.setValue("");

        cmbPrkFiltroEstado.valueProperty().addListener((obs, old, newV) -> filtrarParqueaderos());
        cmbPrkFiltroTipo.valueProperty().addListener((obs, old, newV) -> filtrarParqueaderos());
        cmbPrkFiltroClasif.valueProperty().addListener((obs, old, newV) -> filtrarParqueaderos());

        // Filtro de fecha para QR
        dpFiltroFechaQR.setValue(null);
        dpFiltroFechaQR.valueProperty().addListener((obs, old, newV) -> filtrarQRPorFecha());

        cargarHistorial();
        cargarAccesosActivos();
        cargarParqueaderos();
    }

    private void filtrarParqueaderos() {
        String estado = cmbPrkFiltroEstado.getValue();
        String tipo = cmbPrkFiltroTipo.getValue();
        Boolean esVisitante = null;
        String clasif = cmbPrkFiltroClasif.getValue();
        if (clasif != null && !clasif.isEmpty()) {
            esVisitante = "Visitante".equals(clasif);
        }
        try {
            if ((estado == null || estado.isEmpty()) && (tipo == null || tipo.isEmpty()) && esVisitante == null) {
                listaParqueaderos.setAll(parqueaderoDAO.findAll());
            } else {
                listaParqueaderos.setAll(parqueaderoDAO.findConFiltros(
                        estado.isEmpty() ? null : estado,
                        tipo.isEmpty() ? null : tipo,
                        esVisitante));
            }
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al filtrar: " + e.getMessage());
        }
    }

    private void filtrarQRPorFecha() {
        LocalDate fecha = dpFiltroFechaQR.getValue();
        try {
            if (fecha == null) {
                listaQR.setAll(qrAccesoDAO.findAll());
            } else {
                listaQR.setAll(qrAccesoDAO.findByFecha(fecha));
            }
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al filtrar por fecha: " + e.getMessage());
        }
    }

    @Override
    public void inicializar() {
        cargarHistorial();
        cargarAccesosActivos();
        cargarParqueaderos();
        limpiar();
    }

    // ── Tab 1: configuración de tabla ─────────────────────────────────────────

    private void configurarTablaQR() {
        colIdQR.setCellValueFactory(new PropertyValueFactory<>("idQr"));
        colIdVisita.setCellValueFactory(new PropertyValueFactory<>("idVisita"));
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoQr"));
        colExpiracion.setCellValueFactory(new PropertyValueFactory<>("fechaExpiracion"));
        colFechaUso.setCellValueFactory(new PropertyValueFactory<>("fechaUso"));

        colUsado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(((QRAcceso) getTableRow().getItem()).isUsado() ? "Sí" : "No");
                }
            }
        });

        tablaQR.setItems(listaQR);
    }

    private void cargarHistorial() {
        try {
            listaQR.setAll(qrAccesoDAO.findAll());
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar historial QR: " + e.getMessage());
        }
    }

    // ── Tab 1: validar QR ─────────────────────────────────────────────────────

    @FXML
    private void validarQR() {
        String codigo = txtCodigoQR.getText().trim();
        if (codigo.isEmpty()) {
            mostrarAlerta("Advertencia", "Ingrese o pegue el código QR a validar.");
            return;
        }

        Integer idVigilante = obtenerIdVigilante();
        if (idVigilante == null) {
            mostrarAlerta("Error", "No hay sesión activa. No se puede identificar al vigilante.");
            return;
        }

        try {
            QRResult resultado = llamarSPValidarQR(codigo, idVigilante);

            lblResultado.setText(resultado.mensaje);
            lblResultado.setStyle(resultado.valido
                    ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
                    : "-fx-text-fill: #c0392b; -fx-font-weight: bold;");

            if (resultado.valido && resultado.infoTexto != null) {
                taInfoAcceso.setText(resultado.infoTexto);
            } else if (!resultado.valido) {
                taInfoAcceso.clear();
            }

            // Refrescar ambas tablas
            cargarHistorial();
            cargarAccesosActivos();

            txtCodigoQR.clear();
            txtCodigoQR.requestFocus();

        } catch (Exception e) {
            lblResultado.setText("ERROR: " + e.getMessage());
            lblResultado.setStyle("-fx-text-fill: #c0392b;");
        }
    }

    /**
     * Llama al stored procedure:
     *   SP_VALIDAR_QR(p_codigo_qr IN, p_id_vigilante IN,
     *                 p_valido OUT NUMBER, p_mensaje OUT VARCHAR2,
     *                 p_cursor OUT SYS_REFCURSOR)
     *
     * Si el QR es válido el SP ya inserta en REGISTROS_ACCESO.
     */
    private QRResult llamarSPValidarQR(String codigo, Integer idVigilante) throws Exception {
        Connection conn = ConexionBD.getInstancia().getConexion();
        String call = "{ CALL SP_VALIDAR_QR(?, ?, ?, ?, ?) }";
        try (CallableStatement cs = conn.prepareCall(call)) {
            cs.setString(1, codigo);
            cs.setInt(2, idVigilante);
            cs.registerOutParameter(3, Types.NUMERIC);
            cs.registerOutParameter(4, Types.VARCHAR);
            cs.registerOutParameter(5, OracleTypes.CURSOR);
            cs.execute();

            boolean valido  = cs.getInt(3) == 1;
            String  mensaje = cs.getString(4);
            String  info    = null;

            // Leer el cursor para mostrar info en pantalla
            try (ResultSet cur = (ResultSet) cs.getObject(5)) {
                if (cur != null && cur.next()) {
                    StringBuilder sb = new StringBuilder();
                    append(sb, "Residente",        cur.getString("residente_nombre"));
                    append(sb, "Documento",        cur.getString("residente_documento"));
                    append(sb, "Apartamento",      cur.getString("numero_apartamento"));
                    append(sb, "Cantidad personas", cur.getString("cantidad_personas"));
                    append(sb, "Documento ID",     cur.getString("tiene_documento_pdf"));
                    append(sb, "Notas",            cur.getString("notas"));
                    append(sb, "Personas",         cur.getString("personas_lista"));
                    append(sb, "Vehículos",        cur.getString("vehiculos_lista"));
                    info = sb.toString().trim();
                }
            } catch (Exception ignored) { /* cursor vacío en ruta de error */ }

            return new QRResult(valido, mensaje, info);
        }
    }

    private void append(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty())
            sb.append(label).append(": ").append(value).append("\n");
    }

    // ── Tab 2: configuración de tabla ─────────────────────────────────────────

    private void configurarTablaAccesos() {
        colAccesoId.setCellValueFactory(new PropertyValueFactory<>("idAcceso"));
        colAccesoVisita.setCellValueFactory(new PropertyValueFactory<>("idVisita"));
        colAccesoResidente.setCellValueFactory(new PropertyValueFactory<>("nombreResidente"));
        colAccesoHoraEntrada.setCellValueFactory(new PropertyValueFactory<>("horaEntrada"));
        tablaAccesos.setItems(listaAccesos);
    }

    private void cargarAccesosActivos() {
        try {
            listaAccesos.setAll(registroAccesoDAO.findActivos());
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar visitantes activos: " + e.getMessage());
        }
    }

    // ── Tab 2: registrar salida ───────────────────────────────────────────────

    @FXML
    private void registrarSalida() {
        RegistroAcceso seleccionado = tablaAccesos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarAlerta("Advertencia", "Seleccione un visitante de la lista para registrar su salida.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar salida");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Registrar salida de la visita #" + seleccionado.getIdVisita()
                + " (" + seleccionado.getNombreResidente() + ")?\n\n"
                + "La visita cambiará a estado FINALIZADA.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            String obs = txtObservacionesSalida.getText();
            registroAccesoDAO.registrarSalida(seleccionado.getIdVisita(), obs);
            txtObservacionesSalida.clear();
            cargarAccesosActivos();
            mostrarAlerta("Éxito", "Salida registrada. La visita ha sido marcada como FINALIZADA.");
        } catch (Exception e) {
            mostrarAlerta("Error", "No se pudo registrar la salida: " + e.getMessage());
        }
    }

    // ── Acciones auxiliares ───────────────────────────────────────────────────

    @FXML
    private void refrescarHistorial() {
        cargarHistorial();
    }

    @FXML
    private void refrescarAccesosActivos() {
        cargarAccesosActivos();
    }

    @FXML
    private void limpiar() {
        txtCodigoQR.clear();
        lblResultado.setText("");
        lblResultado.setStyle("");
        if (taInfoAcceso != null) taInfoAcceso.clear();
    }

    // ── Tab 3: Parqueaderos ───────────────────────────────────────────────────

    private void configurarTablaParqueaderos() {
        colPrkCodigo.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getCodigo()));
        colPrkTipo.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getTipo() != null ? data.getValue().getTipo().name() : ""));
        colPrkEstado.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getEstado() != null ? data.getValue().getEstado().name() : ""));
        colPrkApartamento.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getNumeroApartamento() != null
                                ? data.getValue().getNumeroApartamento() : "—"));
        colPrkPropietario.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getNombrePropietario() != null
                                ? data.getValue().getNombrePropietario() : "Visitantes"));
        colPrkVisitante.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(((Parqueadero) getTableRow().getItem()).isEsVisitante() ? "Sí" : "No");
                }
            }
        });
        tablaParqueaderosPortero.setItems(listaParqueaderos);
    }

    private void cargarParqueaderos() {
        try {
            listaParqueaderos.setAll(parqueaderoDAO.findAll());
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar parqueaderos: " + e.getMessage());
        }
    }

    @FXML
    private void refrescarParqueaderos() {
        cargarParqueaderos();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Integer obtenerIdVigilante() {
        if (SesionUsuario.getUsuarioActual() == null) return null;
        return SesionUsuario.getUsuarioActual().getIdUsuario();
    }

    private void mostrarAlerta(String titulo, String contenido) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }

    // ── DTO interno ───────────────────────────────────────────────────────────

    private static class QRResult {
        final boolean valido;
        final String  mensaje;
        final String  infoTexto;   // datos del cursor (residente, apartamento, etc.)

        QRResult(boolean valido, String mensaje, String infoTexto) {
            this.valido    = valido;
            this.mensaje   = mensaje;
            this.infoTexto = infoTexto;
        }
    }
}
