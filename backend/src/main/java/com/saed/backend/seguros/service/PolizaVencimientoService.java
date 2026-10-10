package com.saed.backend.seguros.service;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.repository.PolizaSeguroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tenant-scoped insurance expiry detection (audit F10-08; plan F10-04). It runs only for the
 * property of the caller's own tenant context and records at most one alert per policy and expiry
 * period (the policy end date), so restarts, retries and several instances never duplicate notices
 * and a missed run is caught up by the next one.
 */
@Service
public class PolizaVencimientoService {

    private static final Logger log = LoggerFactory.getLogger(PolizaVencimientoService.class);

    private final PolizaSeguroRepository repository;
    private final EmailService emailService;

    public PolizaVencimientoService(PolizaSeguroRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    @Transactional
    public int detectarVencimientos(Long idPropiedad, LocalDate hoy) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (idPropiedad == null || ctx == null || ctx.getUserId() == null || !idPropiedad.equals(ctx.getPropertyId())) {
            throw new IllegalStateException("La detección de vencimientos requiere el contexto de la propiedad " + idPropiedad);
        }

        repository.actualizarEstadosVencimiento(idPropiedad);
        List<PolizaSeguroDTO> candidatas = repository.findPorVencerOVencidasByPropiedad(idPropiedad);

        int alertas = 0;
        for (PolizaSeguroDTO poliza : candidatas) {
            if (poliza.getFechaFin() == null) {
                continue;
            }
            String mensaje = mensaje(poliza, hoy);
            String periodo = poliza.getFechaFin().toString();
            if (!repository.registrarAlertaVencimiento(idPropiedad, poliza.getIdPoliza(), periodo, mensaje)) {
                continue;
            }
            alertas++;
            notificarAdministradores(idPropiedad, poliza, mensaje);
        }
        log.info("[Polizas] Propiedad {}: {} alertas de vencimiento nuevas", idPropiedad, alertas);
        return alertas;
    }

    private static String mensaje(PolizaSeguroDTO poliza, LocalDate hoy) {
        long diasRestantes = ChronoUnit.DAYS.between(hoy, poliza.getFechaFin());
        String estado = diasRestantes < 0 ? "VENCIDA" : "PRÓXIMA A VENCER";
        String dias = diasRestantes < 0 ? Math.abs(diasRestantes) + " días de vencimiento" : diasRestantes + " días restantes";
        return String.format(
                "Alerta de Seguro: La póliza %s (%s - %s) está %s. Fecha fin: %s (%s). Gestione la renovación oportuna bajo Ley 675.",
                poliza.getNumeroPoliza(), poliza.getCompaniaAseguradora(), poliza.getRamoCobertura(),
                estado, poliza.getFechaFin(), dias);
    }

    private void notificarAdministradores(Long idPropiedad, PolizaSeguroDTO poliza, String mensaje) {
        String asunto = "Alerta SAED: Renovación de Póliza " + poliza.getNumeroPoliza();
        String html = "<h2>Notificación de Vencimiento de Póliza</h2><p>" + HtmlUtils.htmlEscape(mensaje) + "</p>"
                + "<p>Ingrese al portal administrativo de SAED para gestionar la renovación.</p>";
        for (String email : repository.findEmailsAdministradores(idPropiedad)) {
            if (email == null || email.isBlank()) {
                continue;
            }
            try {
                emailService.enviarHtmlPublico(email, asunto, html);
            } catch (Exception e) {
                // The alert is already recorded in ALERTAS_ADMIN; e-mail is a best-effort extra channel.
                log.warn("[Polizas] No se pudo enviar correo de vencimiento de la póliza {} ({})",
                        poliza.getIdPoliza(), e.getClass().getSimpleName());
            }
        }
    }
}
