package com.edificio.admin.view.controladores;

import com.edificio.admin.config.SesionUsuario;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.CuotaArriendo;
import com.edificio.admin.model.Pago;
import com.edificio.admin.model.enums.EstadoCuota;
import com.edificio.admin.model.enums.MetodoPago;
import com.edificio.admin.service.ContratoService;
import com.edificio.admin.service.PagoService;
import com.edificio.admin.util.ValidadorCampos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PagoController implements ControladorVista {

    // ---- Tabla cuotas ----
    @FXML private TableView<CuotaArriendo>                    tablaCuotas;
    @FXML private TableColumn<CuotaArriendo, Integer>         colIdCuota;
    @FXML private TableColumn<CuotaArriendo, Integer>         colContrato;
    @FXML private TableColumn<CuotaArriendo, Integer>         colAnio;
    @FXML private TableColumn<CuotaArriendo, Integer>         colMes;
    @FXML private TableColumn<CuotaArriendo, LocalDate>       colFechaLimite;
    @FXML private TableColumn<CuotaArriendo, BigDecimal>      colValorTotal;
    @FXML private TableColumn<CuotaArriendo, EstadoCuota>     colEstadoCuota;

    // ---- Tabla pagos de la cuota seleccionada ----
    @FXML private TableView<Pago>                 tablaPagos;
    @FXML private TableColumn<Pago, Integer>      colIdPago;
    @FXML private TableColumn<Pago, LocalDate>    colFechaPago;
    @FXML private TableColumn<Pago, BigDecimal>   colValorPagado;
    @FXML private TableColumn<Pago, MetodoPago>   colMetodo;
    @FXML private TableColumn<Pago, String>       colReferencia;

    // ---- Formulario registro pago ----
    @FXML private DatePicker               dpFechaPago;
    @FXML private TextField                txtValorPagado;
    @FXML private ComboBox<MetodoPago>     cmbMetodo;
    @FXML private TextField                txtReferencia;
    @FXML private TextArea                 txtNotasPago;

    // ---- Filtro por contrato ----
    @FXML private ComboBox<Contrato>       cmbContrato;

    // ---- Resumen ----
    @FXML private Label lblTotalPendientes;
    @FXML private Label lblTotalPagados;

    private final PagoService     pagoService     = new PagoService();
    private final ContratoService contratoService = new ContratoService();
    private final ObservableList<CuotaArriendo> listaCuotas = FXCollections.observableArrayList();
    private final ObservableList<Pago>          listaPagos  = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        configurarTablas();
        cmbMetodo.getItems().addAll(MetodoPago.values());

        // ── Restricciones de entrada ──────────────────────────────────────
        ValidadorCampos.soloDecimalPositivo(txtValorPagado);
        ValidadorCampos.limitar(txtReferencia,  200);   // PAGOS.referencia  VARCHAR2(200)
        ValidadorCampos.limitar(txtNotasPago,   500);   // PAGOS.notas       VARCHAR2(500)

        cargarContratos();
        cargarCuotasPendientes();
    }

    @Override
    public void inicializar() {
        cargarContratos();
        cargarCuotasPendientes();
    }

    private void configurarTablas() {
        colIdCuota.setCellValueFactory(new PropertyValueFactory<>("idCuota"));
        colContrato.setCellValueFactory(new PropertyValueFactory<>("idContrato"));
        colAnio.setCellValueFactory(new PropertyValueFactory<>("anio"));
        colMes.setCellValueFactory(new PropertyValueFactory<>("mes"));
        colFechaLimite.setCellValueFactory(new PropertyValueFactory<>("fechaLimite"));
        colValorTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        colEstadoCuota.setCellValueFactory(new PropertyValueFactory<>("estado"));
        tablaCuotas.setItems(listaCuotas);

        colIdPago.setCellValueFactory(new PropertyValueFactory<>("idPago"));
        colFechaPago.setCellValueFactory(new PropertyValueFactory<>("fechaPago"));
        colValorPagado.setCellValueFactory(new PropertyValueFactory<>("valorPagado"));
        colMetodo.setCellValueFactory(new PropertyValueFactory<>("metodoPago"));
        colReferencia.setCellValueFactory(new PropertyValueFactory<>("referencia"));
        tablaPagos.setItems(listaPagos);

        // Al seleccionar cuota, carga sus pagos
        tablaCuotas.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, nuevo) -> { if (nuevo != null) cargarPagosDeCuota(nuevo.getIdCuota()); });
    }

    private void cargarContratos() {
        try {
            List<Contrato> contratos = contratoService.listarTodos();
            cmbContrato.setItems(FXCollections.observableArrayList(contratos));
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar contratos: " + e.getMessage());
        }
    }

    private void cargarCuotasPendientes() {
        try {
            listaCuotas.clear();
            listaCuotas.addAll(pagoService.listarCuotasPendientes());
            actualizarResumen();
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar cuotas: " + e.getMessage());
        }
    }

    private void cargarPagosDeCuota(Integer idCuota) {
        try {
            listaPagos.clear();
            listaPagos.addAll(pagoService.listarPagosPorCuota(idCuota));
        } catch (Exception e) {
            mostrarAlerta("Error", "Error al cargar pagos: " + e.getMessage());
        }
    }

    @FXML
    private void filtrarPorContrato() {
        Contrato sel = cmbContrato.getValue();
        if (sel == null) { cargarCuotasPendientes(); return; }
        try {
            listaCuotas.clear();
            listaCuotas.addAll(pagoService.listarCuotasPorContrato(sel.getIdContrato()));
            actualizarResumen();
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    @FXML
    private void verPendientes() {
        cmbContrato.setValue(null);
        cargarCuotasPendientes();
    }

    @FXML
    private void registrarPago() {
        CuotaArriendo cuota = tablaCuotas.getSelectionModel().getSelectedItem();
        if (cuota == null) { mostrarAlerta("Advertencia", "Seleccione una cuota de la tabla superior."); return; }
        if (cmbMetodo.getValue() == null) { mostrarAlerta("Advertencia", "Seleccione el método de pago."); return; }
        if (txtValorPagado.getText().isBlank()) {
            mostrarAlerta("Advertencia", "El valor pagado es obligatorio.");
            return;
        }
        try {
            Pago pago = new Pago();
            pago.setIdCuota(cuota.getIdCuota());
            pago.setFechaPago(dpFechaPago.getValue() != null ? dpFechaPago.getValue() : LocalDate.now());
            pago.setValorPagado(new BigDecimal(txtValorPagado.getText().trim().replace(",", "")));
            pago.setMetodoPago(cmbMetodo.getValue());
            pago.setReferencia(txtReferencia.getText().trim());
            pago.setNotas(txtNotasPago.getText().trim());
            // Auditoría: usuario que registra el pago (NOT NULL en BD)
            if (SesionUsuario.getUsuarioActual() != null)
                pago.setRegistradoPor(SesionUsuario.getUsuarioActual().getIdUsuario());

            Integer idPago = pagoService.registrarPago(pago);
            mostrarAlerta("Éxito", "Pago registrado con ID: " + idPago);
            cargarCuotasPendientes();
            cargarPagosDeCuota(cuota.getIdCuota());
            limpiarFormularioPago();
        } catch (NumberFormatException ex) {
            mostrarAlerta("Error", "El valor pagado debe ser un número.");
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage());
        }
    }

    @FXML
    private void limpiarFormularioPago() {
        dpFechaPago.setValue(null);
        txtValorPagado.clear();
        cmbMetodo.setValue(null);
        txtReferencia.clear();
        txtNotasPago.clear();
    }

    private void actualizarResumen() {
        BigDecimal totalPendiente = listaCuotas.stream()
            .filter(c -> c.getEstado() != EstadoCuota.PAGADA)
            .map(CuotaArriendo::getValorTotal)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPagado = listaCuotas.stream()
            .filter(c -> c.getEstado() == EstadoCuota.PAGADA)
            .map(CuotaArriendo::getValorTotal)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotalPendientes.setText("$" + totalPendiente.toPlainString());
        lblTotalPagados.setText("$" + totalPagado.toPlainString());
    }

    private void mostrarAlerta(String titulo, String contenido) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
