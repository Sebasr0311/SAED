package com.edificio.admin.service;

import com.edificio.admin.model.Apartamento;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.Residente;
import com.edificio.admin.model.enums.TipoContrato;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;

/**
 * Servicio de correo electrónico via Gmail SMTP.
 *
 * Carga las plantillas HTML de /templates/correos/ del classpath,
 * sustituye las variables th:text="${varName}" y envía el mensaje como HTML.
 * Todas las operaciones son best-effort: las excepciones se swallean para
 * nunca interrumpir el flujo principal de negocio.
 */
public class EmailService {

    private static final String GMAIL_USER;
    private static final String GMAIL_PASSWORD;
    private static final String GMAIL_FROM;

    static {
        GMAIL_USER     = System.getenv("GMAIL_USER") != null
            ? System.getenv("GMAIL_USER") : "gestion.residencias.upc@gmail.com";
        GMAIL_PASSWORD = System.getenv("GMAIL_APP_PASSWORD") != null
            ? System.getenv("GMAIL_APP_PASSWORD")
            : (System.getenv("GMAIL_PASSWORD") != null
                ? System.getenv("GMAIL_PASSWORD") : "Residencial2026");
        GMAIL_FROM     = System.getenv("GMAIL_FROM") != null
            ? System.getenv("GMAIL_FROM") : GMAIL_USER;
    }

    private static final String TEMPLATE_PATH  = "/templates/correos/";

    /** Formato de fecha para el correo: 31/12/2025 */
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Formato numérico colombiano: 1.200.000 (sin símbolo de moneda) */
    private static final NumberFormat NUMBER_FMT;
    static {
        NUMBER_FMT = NumberFormat.getNumberInstance(new Locale("es", "CO"));
        NUMBER_FMT.setMaximumFractionDigits(0);
        NUMBER_FMT.setMinimumFractionDigits(0);
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Envía un correo HTML al residente cuando se crea un contrato.
     * Selecciona la plantilla según TipoContrato.
     *
     * @param destinatario            Email del residente (null/vacío → no hace nada)
     * @param residente               Objeto Residente (nombre para el saludo)
     * @param contrato                Contrato recién creado (tipo, valor, fechas)
     * @param apto                    Apartamento del contrato (número, piso)
     * @param fechaVencimientoAnterior Fecha fin del contrato previo; solo aplica
     *                                para RENOVACION; pasar null en otros casos
     */
    public static void enviarEmailContrato(String destinatario,
                                           Residente residente,
                                           Contrato contrato,
                                           Apartamento apto,
                                           LocalDate fechaVencimientoAnterior) throws Exception {
        if (destinatario == null || destinatario.isBlank()) return;

        TipoContrato tipo = contrato.getTipoContrato() != null
            ? contrato.getTipoContrato() : TipoContrato.INICIAL;

        String html = cargarPlantilla(tipo);
        html = renderizar(html, residente, contrato, apto, fechaVencimientoAnterior);

        String asunto = construirAsunto(tipo, apto);
        enviar(destinatario, asunto, html);
    }

    // ── privado: plantilla ────────────────────────────────────────────────────

    private static String cargarPlantilla(TipoContrato tipo) throws Exception {
        String file = switch (tipo) {
            case PERMANENCIA -> "correo_contrato_permanencia.html";
            case RENOVACION  -> "correo_contrato_renovacion.html";
            default          -> "correo_contrato_inicial.html";
        };

        InputStream is = EmailService.class.getResourceAsStream(TEMPLATE_PATH + file);
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(TEMPLATE_PATH + file);
        }
        if (is == null) {
            throw new Exception("Plantilla de correo no encontrada: " + file);
        }
        byte[] bytes = is.readAllBytes();
        is.close();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ── privado: sustitución de variables ────────────────────────────────────

    /**
     * Sustituye cada ocurrencia de:
     *   th:text="${varName}">FallbackContent<
     * por:
     *   >valorReal<
     *
     * El atributo xmlns:th no molesta a los clientes de correo (lo ignoran).
     * Al final se eliminan todos los atributos th:* residuales.
     */
    private static String renderizar(String html,
                                     Residente res,
                                     Contrato contrato,
                                     Apartamento apto,
                                     LocalDate fechaVencimientoAnterior) {
        // Variables comunes a todas las plantillas
        String nombre = (res != null)
            ? (safeStr(res.getNombres()) + " " + safeStr(res.getApellidos())).trim()
            : "Residente";
        String numApto = (apto != null) ? safeStr(apto.getNumero()) : "-";
        String piso    = (apto != null && apto.getPiso() != null)
            ? String.valueOf(apto.getPiso()) : "-";

        html = replaceVar(html, "nombreResidente",        nombre);
        html = replaceVar(html, "numeroApartamento",      numApto);
        html = replaceVar(html, "piso",                   piso);
        html = replaceVar(html, "diasHabiles",            "5");
        html = replaceVar(html, "telefonoAdministracion",
                          EdificioConfigService.getTelefonoAdministracion());
        html = replaceVar(html, "correoAdministracion",
                          EdificioConfigService.getCorreoAdministracion());

        // Variables exclusivas de RENOVACION
        if (contrato.getTipoContrato() == TipoContrato.RENOVACION) {
            String fechaVenc = (fechaVencimientoAnterior != null)
                ? fechaVencimientoAnterior.format(DATE_FMT) : "-";
            String canon = (contrato.getValorMensual() != null)
                ? NUMBER_FMT.format(contrato.getValorMensual()) : "0";
            String fechaInicio = (contrato.getFechaInicio() != null)
                ? contrato.getFechaInicio().format(DATE_FMT) : "-";

            html = replaceVar(html, "fechaVencimiento",      fechaVenc);
            html = replaceVar(html, "nuevoCanon",            canon);
            html = replaceVar(html, "fechaInicioRenovacion", fechaInicio);
        }

        // Eliminar atributos Thymeleaf residuales y la declaración xmlns:th
        html = html.replaceAll("\\s+th:text=\"[^\"]*\"", "");
        html = html.replaceAll("\\s+xmlns:th=\"[^\"]*\"", "");

        return html;
    }

    /**
     * Reemplaza:  th:text="${varName}">FallbackText<
     * con:        >value<
     *
     * Usa Matcher.quoteReplacement para escapar $ y \ en el valor.
     * El patrón [^<]* captura el contenido de fallback (puede ser vacío o tener texto).
     */
    private static String replaceVar(String html, String varName, String value) {
        String safe = (value != null) ? value : "";
        return html.replaceAll(
            "th:text=\"\\$\\{" + varName + "\\}\">[^<]*<",
            ">" + Matcher.quoteReplacement(safe) + "<"
        );
    }

    private static String safeStr(String s) {
        return (s != null) ? s : "";
    }

    // ── privado: asunto ───────────────────────────────────────────────────────

    private static String construirAsunto(TipoContrato tipo, Apartamento apto) {
        String tipoLabel = switch (tipo) {
            case PERMANENCIA -> "Contrato de Permanencia";
            case RENOVACION  -> "Renovación de Contrato";
            default          -> "Contrato de Arrendamiento";
        };
        String numApto = (apto != null && apto.getNumero() != null)
            ? " — Apto. " + apto.getNumero() : "";
        return tipoLabel + numApto + " · Torres del Horizonte";
    }

    // ── privado: envío SMTP ───────────────────────────────────────────────────

    private static void enviar(String destinatario,
                                String asunto,
                                String htmlBody) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth",               "true");
        props.put("mail.smtp.starttls.enable",    "true");
        props.put("mail.smtp.host",               "smtp.gmail.com");
        props.put("mail.smtp.port",               "587");
        props.put("mail.smtp.ssl.trust",          "smtp.gmail.com");
        props.put("mail.smtp.connectiontimeout",  "10000");
        props.put("mail.smtp.timeout",            "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(GMAIL_USER, GMAIL_PASSWORD);
            }
        });
        session.setDebug(true);

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(GMAIL_FROM,
            "Administración · Torres del Horizonte", "UTF-8"));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
        msg.setSubject(MimeUtility.encodeText(asunto, "UTF-8", "Q"));

        // Parte HTML
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

        Multipart multipart = new MimeMultipart("alternative");
        multipart.addBodyPart(htmlPart);
        msg.setContent(multipart);

        Transport.send(msg);
        System.out.println("[EmailService] Correo enviado a " + destinatario
            + " — " + asunto);
    }
}
