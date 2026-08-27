package com.saed.backend.common.service;

import com.saed.backend.finanzas.dto.ContratoDetalleDTO;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateRenderService templateService;

    public EmailService(JavaMailSender mailSender, TemplateRenderService templateService) {
        this.mailSender = mailSender;
        this.templateService = templateService;
    }

    private void enviarHtml(String destinatario, String asunto, String html, byte[] pdfAdjunto, String pdfNombre) throws Exception {
        if (destinatario == null || destinatario.isBlank()) return;
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(destinatario);
        helper.setSubject(asunto);
        helper.setText(html, true);
        if (pdfAdjunto != null && pdfNombre != null) {
            helper.addAttachment(pdfNombre, new ByteArrayResource(pdfAdjunto));
        }
        mailSender.send(message);
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
                "<div style=\"margin: 20px 0;\"><img src=\"cid:qrImage\" alt=\"QR Code\" style=\"width:200px; height:200px;\"/></div>" +
                "<p>Token manual: " + tokenQR + "</p>" +
                "</body></html>";
        enviarHtml(destinatario, "Codigo QR de Acceso", html, null, null); // Note: inline QR cid attachment could be added
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
