package com.saed.backend.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.finanzas.dto.ContratoDetalleDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EmailService — envío de correos via API HTTP de Brevo v3 (flujo SAED 1.0).
 *
 * Reemplaza JavaMailSender/SMTP por la API de Brevo (https://api.brevo.com/v3),
 * que es el flujo que ya funcionaba en produccion en SAED 1.0 y que Gmail no
 * bloquea desde hosts cloud (Render). Requiere BREVO_API_KEY en el entorno.
 *
 * Sender: gestion.residencias.upc@gmail.com (verificado en Brevo).
 */
@Service
public class EmailService {

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String BREVO_SENDER = "gestion.residencias.upc@gmail.com";
    private static final String BREVO_SENDER_NAME = "SAED";

    private final TemplateRenderService templateService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Lee la API key una sola vez (como el resto de las variables de entorno
    // del backend: WOMPI_*, etc.). Si falta, el envío falla con mensaje claro.
    private static final String BREVO_API_KEY = System.getenv("BREVO_API_KEY");

    public EmailService(TemplateRenderService templateService, ObjectMapper objectMapper) {
        this.templateService = templateService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).build();
    }

    /** Envía un HTML (opcionalmente con un PDF adjunto en base64) via Brevo v3. */
    private void enviarHtml(String destinatario, String asunto, String html, byte[] pdfAdjunto, String pdfNombre) throws Exception {
        if (destinatario == null || destinatario.isBlank()) return;
        if (BREVO_API_KEY == null || BREVO_API_KEY.isBlank()) {
            throw new IllegalStateException("Brevo no configurado: falta BREVO_API_KEY en el entorno.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", BREVO_SENDER_NAME, "email", BREVO_SENDER));
        payload.put("to", List.of(Map.of("email", destinatario)));
        payload.put("subject", asunto);
        payload.put("htmlContent", html);

        if (pdfAdjunto != null && pdfAdjunto.length > 0 && pdfNombre != null && !pdfNombre.isBlank()) {
            List<Map<String, String>> attachments = new ArrayList<>();
            attachments.add(Map.of(
                "content", Base64.getEncoder().encodeToString(pdfAdjunto),
                "name", pdfNombre,
                "type", "application/pdf"
            ));
            payload.put("attachment", attachments);
        }

        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BREVO_URL))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("api-key", BREVO_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                "Brevo rechazó el envío a " + destinatario + " (HTTP " + response.statusCode() + "): " + response.body()
            );
        }
    }

    public void enviarEmailContrato(String destinatario, ContratoDetalleDTO detalle, byte[] pdfAdjunto, String pdfNombre) throws Exception {
        String html = templateService.renderizar(detalle.getTipoContrato(), detalle);
        String asunto = "Nuevo Contrato SAED: " + detalle.getNumeroApartamento();
        enviarHtml(destinatario, asunto, html, pdfAdjunto, pdfNombre);
    }

    public void enviarReciboPago(String destinatario, String concepto, BigDecimal monto, String referencia, String fecha) throws Exception {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>" +
                "<body style=\"font-family: Arial; padding: 20px;\">" +
                "<h2>Confirmacion de Pago Recibido</h2>" +
                "<p>Hemos recibido exitosamente el pago correspondiente a <strong>" + concepto + "</strong>.</p>" +
                "<ul><li>Referencia: " + referencia + "</li>" +
                "<li>Monto: $" + monto + "</li>" +
                "<li>Fecha: " + fecha + "</li></ul>" +
                "<p>Gracias por mantener sus obligaciones al dia.</p>" +
                "</body></html>";
        enviarHtml(destinatario, "Confirmacion de Pago: " + referencia, html, null, null);
    }

    public void enviarCorreoQR(String destinatario, String tokenQR, String fechaExp, String nombreVisitante) throws Exception {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>" +
                "<body style=\"font-family: Arial; padding: 20px;\">" +
                "<h2>Codigo QR de Acceso</h2>" +
                "<p>Se ha generado un acceso para: <strong>" + (nombreVisitante != null ? nombreVisitante : "tu visita") + "</strong>.</p>" +
                "<p>El codigo es valido hasta: " + fechaExp + "</p>" +
                "<p>Token manual: " + tokenQR + "</p>" +
                "</body></html>";
        enviarHtml(destinatario, "Codigo QR de Acceso", html, null, null);
    }

    public void enviarNotificacionPQRS(String destinatario, String radicado, String estado, String respuesta) throws Exception {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>" +
                "<body style=\"font-family: Arial; padding: 20px;\">" +
                "<h2>Notificacion de Peticion/Queja</h2>" +
                "<p>El estado de su caso con radicado <strong>" + radicado + "</strong> es: " + estado + "</p>" +
                (respuesta != null ? "<p>Comentario: " + respuesta + "</p>" : "") +
                "</body></html>";
        enviarHtml(destinatario, "Actualizacion PQRS: " + radicado, html, null, null);
    }

    public void enviarNotificacionMulta(String destinatario, String motivo, BigDecimal monto, String fecha) throws Exception {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>" +
                "<body style=\"font-family: Arial; padding: 20px; color: #333;\">" +
                "<h2 style=\"color: #e53e3e;\">Notificacion de Infraccion</h2>" +
                "<p>Se ha registrado una multa con los siguientes detalles:</p>" +
                "<ul><li>Motivo: " + motivo + "</li>" +
                "<li>Fecha: " + fecha + "</li>" +
                "<li>Valor: $" + monto + "</li></ul>" +
                "<p>Por favor revise su panel de residente para mas informacion y pago.</p>" +
                "</body></html>";
        enviarHtml(destinatario, "Notificacion de Infraccion/Multa", html, null, null);
    }
}