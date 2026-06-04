package com.edificio.admin.service;

import com.edificio.admin.model.Apartamento;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.Residente;
import com.edificio.admin.model.enums.TipoContrato;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;

public class EmailService {

    private static final String GMAIL_USER;
    private static final String GMAIL_PASSWORD;
    private static final String GMAIL_FROM;
    private static final String SENDGRID_API_KEY;

    static {
        GMAIL_USER     = System.getenv("GMAIL_USER") != null
            ? System.getenv("GMAIL_USER") : "gestion.residencias.upc@gmail.com";
        GMAIL_PASSWORD = System.getenv("GMAIL_APP_PASSWORD") != null
            ? System.getenv("GMAIL_APP_PASSWORD")
            : (System.getenv("GMAIL_PASSWORD") != null
                ? System.getenv("GMAIL_PASSWORD") : "Residencial2026");
        GMAIL_FROM     = System.getenv("GMAIL_FROM") != null
            ? System.getenv("GMAIL_FROM") : GMAIL_USER;
        SENDGRID_API_KEY = System.getenv("SENDGRID_API_KEY");
    }

    private static final String TEMPLATE_PATH  = "/templates/correos/";

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final NumberFormat NUMBER_FMT;
    static {
        NUMBER_FMT = NumberFormat.getNumberInstance(new Locale("es", "CO"));
        NUMBER_FMT.setMaximumFractionDigits(0);
        NUMBER_FMT.setMinimumFractionDigits(0);
    }

    // ── API pública ───────────────────────────────────────────────────────────

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

        // 1) Intentar SMTP directo
        try {
            enviarViaSMTP(destinatario, asunto, html);
            return;
        } catch (Exception e) {
            System.err.println("[EmailService] SMTP fallo: " + e.getMessage());
        }

        // 2) Fallback: SendGrid via HTTPS (puerto 443, no bloqueado por Railway)
        if (SENDGRID_API_KEY != null && !SENDGRID_API_KEY.isBlank()) {
            enviarViaSendGrid(destinatario, asunto, html);
        } else {
            throw new Exception("No se pudo enviar el correo (SMTP bloqueado y SENDGRID_API_KEY no configurada). "
                + "Agrega el plugin SendGrid en Railway o configura SENDGRID_API_KEY.");
        }
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

    private static String renderizar(String html,
                                     Residente res,
                                     Contrato contrato,
                                     Apartamento apto,
                                     LocalDate fechaVencimientoAnterior) {
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

        html = html.replaceAll("\\s+th:text=\"[^\"]*\"", "");
        html = html.replaceAll("\\s+xmlns:th=\"[^\"]*\"", "");

        return html;
    }

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

    private static void enviarViaSMTP(String destinatario,
                                      String asunto,
                                      String htmlBody) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth",               "true");
        props.put("mail.smtp.host",               "smtp.gmail.com");
        props.put("mail.smtp.port",               "465");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "false");
        props.put("mail.smtp.ssl.trust",          "smtp.gmail.com");
        props.put("mail.smtp.connectiontimeout",  "15000");
        props.put("mail.smtp.timeout",            "15000");

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

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

        Multipart multipart = new MimeMultipart("alternative");
        multipart.addBodyPart(htmlPart);
        msg.setContent(multipart);

        Transport.send(msg);
        System.out.println("[EmailService] Correo enviado por SMTP a " + destinatario);
    }

    // ── privado: envío SendGrid (HTTPS) ───────────────────────────────────────

    private static void enviarViaSendGrid(String destinatario,
                                          String asunto,
                                          String htmlBody) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        String json = "{\"personalizations\":[{\"to\":[{\"email\":\""
            + jsonEscape(destinatario) + "\"}]}],"
            + "\"from\":{\"email\":\"" + jsonEscape(GMAIL_FROM)
            + "\",\"name\":\"Administración · Torres del Horizonte\"},"
            + "\"subject\":\"" + jsonEscape(asunto) + "\","
            + "\"content\":[{\"type\":\"text/html\",\"value\":\""
            + jsonEscape(htmlBody) + "\"}]}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
            .header("Authorization", "Bearer " + SENDGRID_API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            System.out.println("[EmailService] Correo enviado por SendGrid a " + destinatario);
        } else {
            throw new Exception("SendGrid respondió con estado " + status
                + ": " + response.body());
        }
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
