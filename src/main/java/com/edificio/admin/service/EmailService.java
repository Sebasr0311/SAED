package com.edificio.admin.service;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

/**
 * Servicio de correo electrónico via Gmail SMTP.
 * Usado actualmente para notificar al residente cuando se crea un contrato.
 */
public class EmailService {

    private static final String GMAIL_USER     = "gestion.residencias.upc@gmail.com";
    private static final String GMAIL_PASSWORD = "Residencial2026";
    private static final String GMAIL_FROM     = "gestion.residencias.upc@gmail.com";

    /**
     * Envía un correo de bienvenida al residente cuando se le asigna un contrato.
     *
     * @param destinatario      Email del residente (puede ser null/vacío → método no hace nada)
     * @param nombreResidente   Nombre completo del residente
     * @param idContrato        ID del contrato recién creado
     * @param numeroApartamento Número del apartamento
     */
    public static void enviarEmailContrato(String destinatario,
                                           String nombreResidente,
                                           int    idContrato,
                                           String numeroApartamento) {
        if (destinatario == null || destinatario.isBlank()) return;

        Properties props = new Properties();
        props.put("mail.smtp.auth",               "true");
        props.put("mail.smtp.starttls.enable",     "true");
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

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(GMAIL_FROM, "Administración Residencias UPC", "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            message.setSubject("Contrato de Arrendamiento - Apartamento " + numeroApartamento);

            String body =
                "Estimado/a " + nombreResidente + ",\n\n" +
                "Le informamos que se ha generado el Contrato #" + idContrato + " de arrendamiento " +
                "a su nombre para el Apartamento " + numeroApartamento + ".\n\n" +
                "El contrato se encuentra actualmente en estado PENDIENTE DE FIRMA. " +
                "Por favor comuníquese con la administración del edificio para coordinar " +
                "la revisión y firma del documento.\n\n" +
                "Si tiene alguna duda, responda a este correo o contáctenos directamente.\n\n" +
                "Atentamente,\n" +
                "Administración de Residencias UPC\n" +
                GMAIL_FROM;

            message.setText(body, "UTF-8");
            Transport.send(message);
            System.out.println("[EmailService] Correo enviado a " + destinatario +
                               " (contrato #" + idContrato + ", apto " + numeroApartamento + ")");
        } catch (Exception e) {
            // No interrumpir el flujo principal si el correo falla
            System.err.println("[EmailService] Error enviando correo a " + destinatario +
                               ": " + e.getMessage());
        }
    }
}
