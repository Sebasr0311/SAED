package com.saed.backend.common.service;

import com.saed.backend.finanzas.dto.ContratoDetalleDTO;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateRenderService templateService;

    public EmailService(JavaMailSender mailSender, TemplateRenderService templateService) {
        this.mailSender = mailSender;
        this.templateService = templateService;
    }

    public void enviarEmailContrato(String destinatario, ContratoDetalleDTO detalle, byte[] pdfAdjunto, String pdfNombre) throws Exception {
        if (destinatario == null || destinatario.isBlank()) return;

        String html = templateService.renderizar(detalle.getTipoContrato(), detalle);
        String asunto = "Nuevo Contrato SAED: " + detalle.getNumeroApartamento();

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
}
