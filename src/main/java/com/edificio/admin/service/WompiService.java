package com.edificio.admin.service;

import com.edificio.admin.dao.*;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.model.*;
import com.edificio.admin.model.enums.MetodoPago;
import com.edificio.admin.model.enums.EstadoMulta;
import com.edificio.admin.rest.JsonUtil;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Intenciones de pago con la pasarela Wompi (Plan de pagos del residente).
 *
 * Flujo:
 *  1) POST /api/pagos/wompi/solicitud  -> crea intencion PENDIENTE + firma de integridad
 *  2) El widget de Wompi (frontend) cobra usando publicKey + firma
 *  3) POST /api/wompi/webhook          -> evento transaction.updated (firma HMAC)
 *     Si APPROVED -> ejecuta la logica de negocio existente (cuota/multa) + recibo
 *  4) Reconciliacion periodica         -> resuelve pendientes que perdieron el webhook
 *
 * Los montos a Wompi van en CENTAVOS COP (la API no usa decimales).
 * La firma de integridad SIEMPRE se genera en el servidor (nunca en el frontend).
 */
public class WompiService {

    private static final String WOMPI_PUBLIC_KEY;
    private static final String WOMPI_INTEGRITY_SECRET;
    private static final String WOMPI_EVENTS_SECRET;
    private static final String WOMPI_BASE;

    static {
        WOMPI_PUBLIC_KEY       = System.getenv("WOMPI_PUBLIC_KEY");
        WOMPI_INTEGRITY_SECRET = System.getenv("WOMPI_INTEGRITY_SECRET");
        WOMPI_EVENTS_SECRET    = System.getenv("WOMPI_EVENTS_SECRET");
        String env = System.getenv("WOMPI_ENV");
        boolean sandbox = env == null || env.isBlank() || "sandbox".equalsIgnoreCase(env) || env.startsWith("test");
        WOMPI_BASE = sandbox ? "https://sandbox.wompi.co/v1" : "https://production.wompi.co/v1";
    }

    private final WompiPagoDAO        wompiDAO = new WompiPagoDAO();
    private final CuotaArriendoDAO    cuotaDAO = new CuotaArriendoDAO();
    private final MultaDAO            multaDAO = new MultaDAO();
    private final ContratoDAO         contratoDAO = new ContratoDAO();
    private final PagoService         pagoService = new PagoService();

    /** True cuando hay llaves configuradas (para dar mensajes claros si no). */
    public boolean configurado() {
        return WOMPI_PUBLIC_KEY != null && !WOMPI_PUBLIC_KEY.isBlank()
            && WOMPI_INTEGRITY_SECRET != null && !WOMPI_INTEGRITY_SECRET.isBlank();
    }

    /** Resuelve el id de apartamento de una cuota o multa (para validar propiedad). */
    public Integer apartamentoDe(String concepto, Integer idItem) throws Exception {
        if ("CUOTA".equals(concepto)) {
            CuotaArriendo c = cuotaDAO.findById(idItem);
            if (c == null || c.getIdContrato() == null) return null;
            Contrato ct = contratoDAO.findById(c.getIdContrato());
            return ct != null ? ct.getIdApartamento() : null;
        }
        if ("MULTA".equals(concepto)) {
            Multa m = multaDAO.findById(idItem);
            return m != null ? m.getIdApartamento() : null;
        }
        return null;
    }

    /**
     * Crea la intencion de pago PENDIENTE y devuelve lo que el frontend necesita
     * para abrir el widget de Wompi: {referencia, montoCentavos, publicKey, firmaIntegridad}.
     */
    public Map<String, Object> crearIntencion(String concepto, Integer idItem,
                                              Integer idApartamento, Integer idUsuario) throws Exception {
        if (!configurado())
            throw new DatosInvalidosException("Wompi no esta configurado: define WOMPI_PUBLIC_KEY y WOMPI_INTEGRITY_SECRET.");
        if (!"CUOTA".equals(concepto) && !"MULTA".equals(concepto))
            throw new DatosInvalidosException("Concepto invalido (CUOTA|MULTA).");
        if (idItem == null || idItem <= 0)
            throw new DatosInvalidosException("El item de pago es obligatorio.");

        // Idempotencia (opcion B aprobada en Fase 5.1): si ya existe una intencion
        // PENDIENTE para este item, se devuelve la misma (retomar el pago) en vez de
        // fallar con ORA-00001 del indice unico funcional (UQ_TPAGO_*_PEND).
        WompiPago existente = wompiDAO.findPendientePorItem(concepto, idItem);
        if (existente != null) {
            Map<String, Object> res = new HashMap<>();
            res.put("id", existente.getId());
            res.put("referencia", existente.getReferencia());
            res.put("montoCentavos", existente.getMontoCentavos());
            res.put("publicKey", WOMPI_PUBLIC_KEY);
            res.put("firmaIntegridad", firmaIntegridad(existente.getReferencia(), existente.getMontoCentavos()));
            res.put("idTransaccionWompi", existente.getIdTransaccionWompi());
            res.put("reintento", true);
            return res;
        }

        BigDecimal saldoPesos = saldoEnPesos(concepto, idItem);

        // Referencia unica de SAED (max 255 chars)
        String referencia = "SAED-" + concepto + "-" + idItem + "-" + System.currentTimeMillis();

        WompiPago w = new WompiPago();
        w.setReferencia(referencia);
        w.setIdApartamento(idApartamento);
        w.setIdUsuario(idUsuario);
        w.setConcepto(concepto);
        if ("CUOTA".equals(concepto)) w.setIdCuota(idItem); else w.setIdMulta(idItem);
        w.setMontoCentavos(saldoPesos.movePointRight(2).longValueExact());
        w.setMoneda("COP");
        w.setEstado("PENDIENTE");
        Integer id = wompiDAO.insert(w);
        w.setId(id);

        Map<String, Object> res = new HashMap<>();
        res.put("id", id);
        res.put("referencia", referencia);
        res.put("montoCentavos", w.getMontoCentavos());
        res.put("publicKey", WOMPI_PUBLIC_KEY);
        res.put("firmaIntegridad", firmaIntegridad(referencia, w.getMontoCentavos()));
        return res;
    }

    /** Saldo pendiente en pesos: cuota = valorTotal - totalPagado; multa = monto. */
    private BigDecimal saldoEnPesos(String concepto, Integer idItem) throws Exception {
        if ("CUOTA".equals(concepto)) {
            CuotaArriendo c = pagoService.buscarCuotaPorId(idItem);
            BigDecimal total = c.getValorTotal();
            BigDecimal pagado = pagoService.listarPagosPorCuota(idItem).stream()
                .map(Pago::getValorPagado).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal saldo = total.subtract(pagado);
            if (saldo.compareTo(BigDecimal.ZERO) <= 0)
                throw new DatosInvalidosException("La cuota ya esta pagada.");
            if (c.getEstado() != null && "PAGADA".equalsIgnoreCase(c.getEstado().name()))
                throw new DatosInvalidosException("La cuota ya esta pagada.");
            return saldo;
        }
        Multa m = multaDAO.findById(idItem);
        if (m == null) throw new DatosInvalidosException("Multa no encontrada.");
        if (m.getEstado() == EstadoMulta.PAGADA)
            throw new DatosInvalidosException("La multa ya esta pagada.");
        if (m.getEstado() == EstadoMulta.ANULADA)
            throw new DatosInvalidosException("La multa esta anulada.");
        if (m.getMonto() == null || m.getMonto().compareTo(BigDecimal.ZERO) <= 0)
            throw new DatosInvalidosException("La multa no tiene un monto valido.");
        return m.getMonto();
    }

    /** SHA256("<referencia><montoCentavos>COP<integritySecret>") — firma del widget. */
    public String firmaIntegridad(String referencia, long montoCentavos) throws Exception {
        String s = referencia + montoCentavos + "COP" + WOMPI_INTEGRITY_SECRET;
        return sha256Hex(s);
    }

    /** Consulta el estado actual en Wompi (GET /v1/transactions/{id}, llave publica). */
    public String consultarTransaccion(String idTransaccionWompi) throws Exception {
        if (idTransaccionWompi == null || idTransaccionWompi.isBlank()
            || WOMPI_PUBLIC_KEY == null || WOMPI_PUBLIC_KEY.isBlank()) return null;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(WOMPI_BASE + "/transactions/" + idTransaccionWompi))
            .header("Authorization", "Bearer " + WOMPI_PUBLIC_KEY)
            .timeout(Duration.ofSeconds(20))
            .GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) return null; // transaccion inexistente
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            Map<String, Object> root = JsonUtil.fromJson(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) root.get("data");
            return data != null ? (String) data.get("status") : null;
        }
        System.err.println("[Wompi] consulta transaccion " + idTransaccionWompi
            + " -> HTTP " + resp.statusCode() + ": " + resp.body());
        return null;
    }

    /**
     * Procesa un evento del webhook (transaction.updated).
     * Valida la firma (checksum); es idempotente; ejecuta la logica de negocio
     * SOLO en la transicion PENDIENTE -> APPROVED.
     */
    public void procesarWebhook(String bodyRaw) throws Exception {
        if (bodyRaw == null || bodyRaw.isBlank()) throw new DatosInvalidosException("Body vacio");

        Map<String, Object> evento;
        try {
            evento = JsonUtil.fromJson(bodyRaw, Map.class);
        } catch (Exception e) {
            throw new DatosInvalidosException("JSON invalido: " + e.getMessage());
        }

        String event = (String) evento.get("event");
        if (!"transaction.updated".equals(event)) return; // ignorar otros eventos

        // Seguridad: firmado con SHA256 (properties + timestamp + events secret)
        if (!verificarChecksum(evento)) {
            System.err.println("[Wompi] checksum invalido — evento ignorado");
            return; // el handler responde 200 sin procesar
        }

        Map<String, Object> data  = (Map<String, Object>) evento.get("data");
        if (data == null) return;
        Map<String, Object> tx = (Map<String, Object>) data.get("transaction");
        if (tx == null) return;

        String referencia = (String) tx.get("reference");
        String idWompi    = (String) tx.get("id");
        String status     = (String) tx.get("status");
        Object montoObj   = tx.get("amount_in_cents");
        if (referencia == null || status == null) return;

        WompiPago intencion = wompiDAO.findByReferencia(referencia);
        if (intencion == null) {
            System.err.println("[Wompi] referencia desconocida: " + referencia);
            return;
        }

        // Idempotencia: un evento repetido no debe re-ejecutar el negocio
        if (!"PENDIENTE".equals(intencion.getEstado())) return;

        String nuevoEstado = estadoInternoDe(status);
        boolean aprobada = "APROBADO".equals(nuevoEstado);

        // Guardar payload crudo (auditoria) + datos de Wompi
        wompiDAO.marcarEstado(intencion.getId(), nuevoEstado, idWompi,
            (String) tx.get("payment_method_type"), bodyRaw, aprobada);
        intencion.setEstado(nuevoEstado);
        intencion.setIdTransaccionWompi(idWompi);

        if (aprobada) {
            ejecutarNegocio(intencion, bodyRaw);
        }
    }

    /** Mapea el estado de Wompi al interno. */
    private String estadoInternoDe(String statusWompi) {
        if (statusWompi == null) return "ERROR";
        switch (statusWompi.toUpperCase()) {
            case "APPROVED": return "APROBADO";
            case "DECLINED": return "RECHAZADO";
            case "VOIDED":   return "VENCIDO";
            case "ERROR":    return "ERROR";
            default:         return "PENDIENTE";
        }
    }

    /**
     * Reconciliacion periodica: resuelve intenciones PENDIENTES que perdieron el
     * webhook (caida, retry agotado). Las que tienen transaccion se consultan en
     * Wompi; las que nunca crearon transaccion se marcan VENCIDO.
     */
    public void reconciliarPendientes() {
        try {
            List<WompiPago> conTx = wompiDAO.findPendientesAntiguos(30);
            for (WompiPago w : conTx) {
                String status = consultarTransaccion(w.getIdTransaccionWompi());
                if (status == null) continue;
                String interno = estadoInternoDe(status);
                if ("PENDIENTE".equals(interno)) continue;
            wompiDAO.marcarEstado(w.getId(), interno, null, null,
                "Reconciliado: " + status, "APROBADO".equals(interno));
            if ("APROBADO".equals(interno)) {
                ejecutarNegocio(w, null);
            }
            }
            List<WompiPago> sinTx = wompiDAO.findPendientesSinTransaccion(120);
            for (WompiPago w : sinTx) {
                wompiDAO.marcarEstado(w.getId(), "VENCIDO", null, null,
                    "Vencido por inactividad (sin transaccion en Wompi)", false);
            }
        } catch (Exception e) {
            System.err.println("[Wompi] reconciliacion fallo: " + e.getMessage());
        }
    }

    /**
     * Ejecuta la logica de negocio existente dentro de UNA transaccion atomica
     * (la conexion es ThreadLocal, asi que los DAO comparten el mismo commit):
     *  - CUOTA -> registra Pago (metodo WOMPI) y marca la cuota PAGADA si se cubre
     *  - MULTA -> marca la multa PAGADA (MultaDAO.pagar)
     * Luego envia el recibo por correo (fuera de la transaccion).
     */
    private void ejecutarNegocio(WompiPago intencion, String payloadWebhook) throws Exception {
        Connection conn = ConexionBD.getInstancia().getConexion();
        conn.setAutoCommit(false);
        try {
            if ("CUOTA".equals(intencion.getConcepto())) {
                Pago p = new Pago();
                p.setIdCuota(intencion.getIdCuota());
                p.setFechaPago(LocalDate.now());
                p.setValorPagado(intencion.montoEnPesos());
                p.setMetodoPago(MetodoPago.WOMPI);
                p.setReferencia(intencion.getReferencia());
                p.setRegistradoPor(intencion.getIdUsuario());
                p.setNotas("Pago online Wompi (ref " + intencion.getReferencia() + ")");
                pagoService.registrarPago(p); // inserta PAGO + marca la cuota si se cubre
            } else {
                multaDAO.pagar(intencion.getIdMulta(), intencion.getIdUsuario(), "WOMPI");
            }
            conn.commit();
        } catch (Exception e) {
            try { conn.rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { conn.setAutoCommit(true); } catch (Exception ignored) {}
        }

        // Recibo por correo (fuera de la transaccion de BD)
        try {
            enviarRecibo(intencion);
        } catch (Exception e) {
            System.err.println("[Wompi] fallo al enviar recibo: " + e.getMessage());
        }
    }

    /** Envia el recibo de pago al residente (EmailService + Brevo). */
    private void enviarRecibo(WompiPago intencion) throws Exception {
        ResidenteDAO resDAO = new ResidenteDAO();
        Residente residente = null;
        if (intencion.getIdApartamento() != null) {
            Apartamento apto = new ApartamentoDAO().findById(intencion.getIdApartamento());
            if (apto != null) {
                List<Contrato> contratos = contratoDAO.findByApartamento(intencion.getIdApartamento());
                for (Contrato c : contratos) {
                    if (c.getEstado() != null && "ACTIVO".equals(c.getEstado().name())) {
                        List<ContratoResidente> rels = new ContratoResidenteDAO().findByContrato(c.getIdContrato());
                        for (ContratoResidente cr : rels) {
                            if ("ARRENDATARIO".equals(cr.getRolEnContrato())) {
                                residente = resDAO.findById(cr.getIdResidente());
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }
        if (residente == null || residente.getEmail() == null || residente.getEmail().isBlank()) return;

        String concepto = "CUOTA".equals(intencion.getConcepto())
            ? "cuota de arriendo/administracion" : "multa";
        BigDecimal monto = intencion.montoEnPesos();
        Map<String, Object> datos = new HashMap<>();
        datos.put("concepto", concepto);
        datos.put("apartamento", String.valueOf(intencion.getIdApartamento()));
        datos.put("monto", monto);
        datos.put("referencia", intencion.getReferencia());
        datos.put("fecha", java.time.LocalDate.now());

        EmailService.enviarReciboPago(residente.getEmail(), residente, datos);
    }

    private String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * Verifica el checksum del evento (firma asimetrica de Wompi):
     * SHA256( concat(valores de signature.properties segun data) + timestamp + eventsSecret )
     * El arreglo properties puede variar por evento; SIEMPRE se extrae del evento.
     */
    boolean verificarChecksum(Map<String, Object> evento) throws Exception {
        if (WOMPI_EVENTS_SECRET == null || WOMPI_EVENTS_SECRET.isBlank()) {
            System.err.println("[Wompi] WOMPI_EVENTS_SECRET no configurado — no se puede validar el webhook");
            return false;
        }
        Map<String, Object> signature = (Map<String, Object>) evento.get("signature");
        if (signature == null) return false;
        List<String> properties = (List<String>) signature.get("properties");
        Object checksum = signature.get("checksum");
        Object timestamp = evento.get("timestamp");
        if (properties == null || checksum == null || timestamp == null) return false;

        Map<String, Object> data = (Map<String, Object>) evento.get("data");
        StringBuilder sb = new StringBuilder();
        for (String prop : properties) {
            Object valor = navegar(data, prop);
            sb.append(valor == null ? "" : normalizarNumero(valor));
        }
        sb.append(normalizarNumero(timestamp));
        sb.append(WOMPI_EVENTS_SECRET);

        String calc = sha256Hex(sb.toString());
        String esperado = String.valueOf(checksum);
        // Wompi devuelve el checksum en mayusculas en algunos ejemplos
        return calc.equalsIgnoreCase(esperado);
    }

    /** Navega el objeto data con una ruta tipo "transaction.id". */
    private Object navegar(Map<String, Object> data, String ruta) {
        Object cur = data;
        for (String parte : ruta.split("\\.")) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<?, ?>) cur).get(parte);
        }
        return cur;
    }

    /**
     * Gson parsea los numeros del JSON como Double; al concatenar, un entero
     * puede salir como "1.2E7" o "12000000.0" y romper el checksum. Se
     * normalizan los numeros integrales a su forma entera sin decimales.
     */
    private Object normalizarNumero(Object v) {
        if (v instanceof Double) {
            double d = (Double) v;
            if (!Double.isInfinite(d) && d == Math.floor(d)) {
                return BigDecimal.valueOf(d).toBigInteger().toString();
            }
            return String.valueOf(d);
        }
        return v;
    }
}
