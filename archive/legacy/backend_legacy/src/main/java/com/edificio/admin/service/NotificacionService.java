package com.edificio.admin.service;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Servicio para compartir el código QR de acceso por distintos canales.
 * No requiere dependencias externas ni credenciales:
 *   - Correo  : abre el cliente de correo local (Outlook / Thunderbird / etc.)
 *               con asunto y cuerpo pre-llenados; el operador pulsa Enviar.
 *   - Telegram: abre Telegram Desktop o el navegador con el texto pre-llenado.
 *   - SMS     : copia el código al portapapeles para que el operador lo pegue
 *               desde su celular; retorna el teléfono como recordatorio.
 */
public class NotificacionService {

    /**
     * Abre el cliente de correo del SO con el código QR en el cuerpo del mensaje.
     *
     * @param email    Dirección del destinatario (visitante).
     * @param codigoQR Código QR generado para la visita.
     */
    public void compartirPorCorreo(String email, String codigoQR) throws Exception {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El visitante no tiene correo registrado.");
        }
        String subject = encode("Código QR de acceso – Edificio");
        String body    = encode("Su código de acceso QR es:\n\n" + codigoQR
                              + "\n\nPresente este código al ingresar al edificio.");
        URI uri = new URI("mailto:" + email
                        + "?subject=" + subject
                        + "&body="    + body);
        Desktop.getDesktop().mail(uri);
    }

    /**
     * Abre Telegram Desktop (o el navegador) con el código QR pre-llenado
     * para que el operador/residente lo reenvíe al visitante.
     *
     * @param codigoQR Código QR generado para la visita.
     */
    public void compartirPorTelegram(String codigoQR) throws Exception {
        String text = encode("Código QR de acceso al edificio: " + codigoQR
                           + "\n\nPresente este código al vigilante al ingresar.");
        URI uri = new URI("https://t.me/share/url?text=" + text);
        Desktop.getDesktop().browse(uri);
    }

    /**
     * Copia el código QR al portapapeles del sistema.
     * El operador puede pegarlo en su aplicación de mensajería / SMS desde el celular.
     *
     * @param codigoQR Código QR generado para la visita.
     * @return El teléfono del visitante para que el operador sepa a quién enviar el SMS.
     */
    public String compartirPorSMS(String telefono, String codigoQR) {
        StringSelection seleccion = new StringSelection(codigoQR);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(seleccion, null);
        return (telefono != null && !telefono.isBlank()) ? telefono : "(sin teléfono registrado)";
    }

    // ---- utilidad privada ----

    /** Codifica un texto para usarlo en una URI (RFC 3986). */
    private String encode(String texto) {
        return java.net.URLEncoder.encode(texto, StandardCharsets.UTF_8)
                                  .replace("+", "%20");
    }
}
