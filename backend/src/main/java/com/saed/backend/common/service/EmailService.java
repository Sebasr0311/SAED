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
    private final org.thymeleaf.TemplateEngine templateEngine;

    // Lee la API key una sola vez (como el resto de las variables de entorno
    // del backend: WOMPI_*, etc.). Si falta, el envío falla con mensaje claro.
    private static final String BREVO_API_KEY = System.getenv("BREVO_API_KEY");

    public EmailService(TemplateRenderService templateService, ObjectMapper objectMapper, org.thymeleaf.TemplateEngine templateEngine) {
        this.templateService = templateService;
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
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

    /** Envío asíncrono no bloqueante para no demorar la respuesta de generación de QR. */
    public void enviarCorreoQRAsync(String destinatario, String tokenQR, String fechaExp, String nombreVisitante) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                enviarCorreoQR(destinatario, tokenQR, fechaExp, nombreVisitante);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(EmailService.class)
                    .warn("Fallo en envío asíncrono de correo QR a {}: {}", destinatario, e.getMessage());
            }
        });
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

    /** Envío público de HTML simple (para avisos masivos desde controllers). */
    public void enviarHtmlPublico(String destinatario, String asunto, String html) throws Exception {
        enviarHtml(destinatario, asunto, html, null, null);
    }

    /**
     * Envía el código OTP de seguridad para la eliminación definitiva de una copropiedad.
     */
    public void enviarCodigoOtpEliminacion(String destinatario, String nombrePropiedad, String codigoOtp, int minutosValidez) throws Exception {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f5f7; margin: 0; padding: 20px; color: #1e293b; }
                    .card { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); }
                    .header { background: #dc2626; color: #ffffff; padding: 24px; text-align: center; }
                    .header h1 { margin: 0; font-size: 20px; font-weight: 700; letter-spacing: -0.5px; }
                    .content { padding: 28px 24px; }
                    .warning-box { background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 14px; margin-bottom: 20px; border-radius: 4px; font-size: 13px; color: #991b1b; }
                    .otp-box { background: #f8fafc; border: 2px dashed #cbd5e1; border-radius: 8px; text-align: center; padding: 18px; margin: 24px 0; }
                    .otp-code { font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #0f172a; margin: 0; }
                    .footer { padding: 16px 24px; background: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 11px; text-align: center; color: #64748b; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="header">
                        <h1>Confirmación de Eliminación Definitiva</h1>
                    </div>
                    <div class="content">
                        <p>Estimado Administrador de Organización,</p>
                        <p>Se ha iniciado una solicitud para <strong>eliminar definitivamente</strong> la siguiente propiedad de la plataforma SAED:</p>
                        <p style="font-size: 16px; font-weight: 600; color: #0f172a; padding: 8px 12px; background: #f1f5f9; border-radius: 6px;">
                            %s
                        </p>
                        <div class="warning-box">
                            <strong>ADVERTENCIA:</strong> Esta acción es destructiva e irreversible. Todas las unidades, accesos, registros y operaciones vinculadas serán removidas de la organización.
                        </div>
                        <p>Para autorizar la verificación de esta operación, ingrese el siguiente código de seguridad:</p>
                        <div class="otp-box">
                            <div class="otp-code">%s</div>
                            <div style="font-size: 12px; color: #64748b; margin-top: 6px;">Válido durante %d minutos</div>
                        </div>
                        <p style="font-size: 12px; color: #64748b;">
                            Si usted no inició esta solicitud, ignore este correo inmediatamente y modifique sus credenciales de acceso de forma preventiva.
                        </p>
                    </div>
                    <div class="footer">
                        SAED 2.0 &bull; Sistema Avanzado de Edificios y Departamentos &bull; Mensaje de Seguridad
                    </div>
                </div>
            </body>
            </html>
            """.formatted(nombrePropiedad, codigoOtp, minutosValidez);

        enviarHtml(destinatario, "CÓDIGO DE SEGURIDAD: Eliminación de " + nombrePropiedad, html, null, null);
    }

    public void enviarBienvenidaCredenciales(
            String destinatario,
            String nombreCompleto,
            String organizacion,
            String planNombre,
            String rol,
            String nombreUsuario,
            String passwordGenerada,
            String urlLogin
    ) throws Exception {
        org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
        context.setVariable("nombreCompleto", nombreCompleto != null ? nombreCompleto : "Usuario");
        context.setVariable("organizacion", organizacion != null ? organizacion : "SAED");
        context.setVariable("planNombre", planNombre != null ? planNombre : "Plan SAED");
        context.setVariable("rol", rol != null ? rol : "ADMIN_ORGANIZACION");
        context.setVariable("rolNombre", rol != null ? rol : "Administrador");
        context.setVariable("rolDescripcion", "Acceso a la plataforma SAED.");
        context.setVariable("nombreUsuario", nombreUsuario);
        context.setVariable("passwordGenerada", passwordGenerada);
        context.setVariable("urlLogin", urlLogin != null && !urlLogin.isBlank() ? urlLogin : "https://saedfront.vercel.app/login");

        String html = templateEngine.process("correos/correo_bienvenida_credenciales", context);
        String asunto = "¡Bienvenido a SAED! — Tus credenciales de acceso";
        enviarHtml(destinatario, asunto, html, null, null);
    }

    public void enviarBienvenidaCredencialesAsync(
            String destinatario,
            String nombreCompleto,
            String organizacion,
            String planNombre,
            String rol,
            String nombreUsuario,
            String passwordGenerada,
            String urlLogin
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                enviarBienvenidaCredenciales(destinatario, nombreCompleto, organizacion, planNombre, rol, nombreUsuario, passwordGenerada, urlLogin);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(EmailService.class)
                        .warn("Fallo en envío asíncrono de bienvenida credenciales a {}: {}", destinatario, e.getMessage());
            }
        });
    }

    public void enviarCredencialesCreadoPorUsuario(
            String destinatario,
            String nombreCompleto,
            String creadoPorNombre,
            String creadoPorRol,
            String organizacion,
            String propiedad,
            String unidad,
            String rol,
            String nombreUsuario,
            String passwordGenerada,
            String urlLogin
    ) throws Exception {
        org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
        context.setVariable("nombreCompleto", nombreCompleto != null ? nombreCompleto : "Usuario");
        context.setVariable("creadoPorNombre", creadoPorNombre != null ? creadoPorNombre : "Un administrador");
        context.setVariable("creadoPorRol", creadoPorRol != null ? creadoPorRol : "ADMIN_ORGANIZACION");
        context.setVariable("creadoPorRolNombre", creadoPorRol != null ? creadoPorRol : "Administrador");
        context.setVariable("organizacion", organizacion);
        context.setVariable("propiedad", propiedad);
        context.setVariable("unidad", unidad);
        context.setVariable("rol", rol != null ? rol : "RESIDENTE");
        context.setVariable("rolNombre", rol != null ? rol : "Usuario");
        context.setVariable("rolDescripcion", "Acceso al portal de SAED.");
        context.setVariable("nombreUsuario", nombreUsuario);
        context.setVariable("passwordGenerada", passwordGenerada);
        context.setVariable("urlLogin", urlLogin != null && !urlLogin.isBlank() ? urlLogin : "https://saedfront.vercel.app/login");

        String html = templateEngine.process("correos/correo_credenciales_creado_por_usuario", context);
        String asunto = "Acceso a SAED — Tus credenciales de inicio de sesión";
        enviarHtml(destinatario, asunto, html, null, null);
    }

    public void enviarCredencialesCreadoPorUsuarioAsync(
            String destinatario,
            String nombreCompleto,
            String creadoPorNombre,
            String creadoPorRol,
            String organizacion,
            String propiedad,
            String unidad,
            String rol,
            String nombreUsuario,
            String passwordGenerada,
            String urlLogin
    ) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                enviarCredencialesCreadoPorUsuario(destinatario, nombreCompleto, creadoPorNombre, creadoPorRol, organizacion, propiedad, unidad, rol, nombreUsuario, passwordGenerada, urlLogin);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(EmailService.class)
                        .warn("Fallo en envío asíncrono de credenciales creadas por usuario a {}: {}", destinatario, e.getMessage());
            }
        });
    }
}