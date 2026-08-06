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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
        enviarEmailContrato(destinatario, residente, contrato, apto,
            fechaVencimientoAnterior, null, null);
    }

    public static void enviarEmailContrato(String destinatario,
                                           Residente residente,
                                           Contrato contrato,
                                           Apartamento apto,
                                           LocalDate fechaVencimientoAnterior,
                                           byte[] pdfAdjunto,
                                           String pdfNombre) throws Exception {
        if (destinatario == null || destinatario.isBlank()) return;

        TipoContrato tipo = contrato.getTipoContrato() != null
            ? contrato.getTipoContrato() : TipoContrato.INICIAL;

        String html = cargarPlantilla(tipo);
        html = renderizar(html, residente, contrato, apto, fechaVencimientoAnterior);

        String asunto = construirAsunto(tipo, apto);

        // 1) Intentar SMTP directo
        try {
            enviarViaSMTP(destinatario, asunto, html, pdfAdjunto, pdfNombre);
            return;
        } catch (Exception e) {
            System.err.println("[EmailService] SMTP fallo: " + e.getMessage());
        }

        // 2) Fallback: SendGrid via HTTPS
        if (SENDGRID_API_KEY != null && !SENDGRID_API_KEY.isBlank()) {
            enviarViaSendGrid(destinatario, asunto, html, pdfAdjunto, pdfNombre);
        } else {
            throw new Exception("No se pudo enviar el correo (SMTP bloqueado y SENDGRID_API_KEY no configurada). "
                + "Agrega el plugin SendGrid en Render o configura SENDGRID_API_KEY.");
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
                                      String htmlBody,
                                      byte[] pdfAdjunto,
                                      String pdfNombre) throws Exception {
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

        Multipart multipart;
        if (pdfAdjunto != null && pdfNombre != null) {
            multipart = new MimeMultipart("mixed");
            multipart.addBodyPart(htmlPart);

            MimeBodyPart pdfPart = new MimeBodyPart();
            pdfPart.setFileName(pdfNombre);
            pdfPart.setContent(pdfAdjunto, "application/pdf");
            pdfPart.setHeader("Content-Transfer-Encoding", "base64");
            multipart.addBodyPart(pdfPart);
        } else {
            multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(htmlPart);
        }

        msg.setContent(multipart);
        Transport.send(msg);
        System.out.println("[EmailService] Correo enviado por SMTP a " + destinatario);
    }

    // ── privado: envío SendGrid (HTTPS) ───────────────────────────────────────

    private static void enviarViaSendGrid(String destinatario,
                                          String asunto,
                                          String htmlBody,
                                          byte[] pdfAdjunto,
                                          String pdfNombre) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        StringBuilder json = new StringBuilder();
        json.append("{\"personalizations\":[{\"to\":[{\"email\":\"")
            .append(jsonEscape(destinatario)).append("\"}]}],")
            .append("\"from\":{\"email\":\"").append(jsonEscape(GMAIL_FROM))
            .append("\",\"name\":\"Administración · Torres del Horizonte\"},"
            + "\"subject\":\"").append(jsonEscape(asunto)).append("\",")
            .append("\"content\":[{\"type\":\"text/html\",\"value\":\"")
            .append(jsonEscape(htmlBody)).append("\"}]");

        if (pdfAdjunto != null && pdfNombre != null) {
            String b64 = Base64.getEncoder().encodeToString(pdfAdjunto);
            json.append(",\"attachments\":[{\"content\":\"")
                .append(b64)
                .append("\",\"type\":\"application/pdf\",\"filename\":\"")
                .append(jsonEscape(pdfNombre)).append("\"}]");
        }

        json.append("}");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
            .header("Authorization", "Bearer " + SENDGRID_API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            System.out.println("[EmailService] Correo enviado por SendGrid a " + destinatario
                + (pdfAdjunto != null ? " (con PDF adjunto)" : ""));
        } else {
            throw new Exception("SendGrid respondió con estado " + status
                + ": " + response.body());
        }
    }

    // ── público: envío de QR por correo ───────────────────────────────────────

    public static void enviarCorreoQR(String destinatario,
                                      String codigoQr,
                                      String nombreVisitante) throws Exception {
        if (destinatario == null || destinatario.isBlank()) return;

        String imgUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data="
            + java.net.URLEncoder.encode(codigoQr, "UTF-8");
        String safeNombre = (nombreVisitante != null) ? nombreVisitante : "tu visita";

        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
            + "<style>"
            + "body{margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif}"
            + ".wrapper{max-width:560px;margin:30px auto;background:#fff;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden}"
            + ".header{background:#0D1B2A;padding:24px;text-align:center}"
            + ".header h2{color:#fff;margin:0;font-size:20px}"
            + ".header p{color:#B8933E;margin:4px 0 0;font-size:12px;text-transform:uppercase;letter-spacing:2px}"
            + ".body{padding:32px;text-align:center;font-size:14px;color:#333}"
            + ".qr-img{margin:16px 0;border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,0.12)}"
            + ".codigo{font-family:monospace;font-size:13px;color:#666;word-break:break-all;margin:8px 0 16px}"
            + ".footer{background:#f8f5ee;padding:20px 32px;text-align:center;font-size:12px;color:#666}"
            + ".footer strong{color:#0D1B2A}"
            + "</style></head><body>"
            + "<div class=\"wrapper\">"
            + "<div class=\"header\"><h2>Torres del Horizonte</h2><p>C\u00f3digo QR de Acceso</p></div>"
            + "<div class=\"body\">"
            + "<p>Hola,</p>"
            + "<p>Has recibido un c\u00f3digo QR de acceso para <strong>" + jsonEscape(safeNombre) + "</strong>.</p>"
            + "<p>Pres\u00e9ntalo en la entrada del edificio para ingresar.</p>"
            + "<img src=\"" + imgUrl + "\" alt=\"QR\" class=\"qr-img\" width=\"250\">"
            + "<p class=\"codigo\">C\u00f3digo: <strong>" + jsonEscape(codigoQr) + "</strong></p>"
            + "</div>"
            + "<div class=\"footer\">"
            + "<strong>SAED \u00b7 Torres del Horizonte</strong><br>"
            + "Este es un mensaje generado autom\u00e1ticamente por el sistema de administraci\u00f3n."
            + "</div></div></body></html>";

        try {
            enviarViaSMTP(destinatario, "C\u00f3digo QR de Acceso \u00b7 Torres del Horizonte", html, null, null);
            return;
        } catch (Exception e) {
            System.err.println("[EmailService] SMTP QR fallo: " + e.getMessage());
        }

        if (SENDGRID_API_KEY != null && !SENDGRID_API_KEY.isBlank()) {
            enviarViaSendGrid(destinatario, "C\u00f3digo QR de Acceso \u00b7 Torres del Horizonte", html, null, null);
        } else {
            throw new Exception("No se pudo enviar el correo (SMTP bloqueado y SENDGRID_API_KEY no configurada).");
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
