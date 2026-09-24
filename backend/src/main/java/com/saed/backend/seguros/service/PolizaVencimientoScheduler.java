package com.saed.backend.seguros.service;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.repository.PolizaSeguroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduler proactivo para la detección y notificación automática
 * de vencimientos de pólizas de seguro de áreas comunes (F10-07).
 */
@Component
public class PolizaVencimientoScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolizaVencimientoScheduler.class);

    private final PolizaSeguroRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    public PolizaVencimientoScheduler(PolizaSeguroRepository repository,
                                      JdbcTemplate jdbcTemplate,
                                      EmailService emailService) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    /**
     * Ejecuta diariamente a las 03:00 AM la verificación de pólizas por vencer o vencidas.
     * Retorna la cantidad de alertas creadas en esta ejecución.
     */
    @Scheduled(cron = "${saed.seguros.vencimiento-cron:0 0 3 * * ?}")
    public int verificarYAlertarVencimientos() {
        log.info("[PolizaScheduler] Iniciando verificación proactiva de vencimientos de pólizas...");
        int alertasGeneradas = 0;

        try {
            List<PolizaSeguroDTO> candidatas = repository.findPolizasProximasAVencerGlobal();
            LocalDate hoy = LocalDate.now();

            for (PolizaSeguroDTO poliza : candidatas) {
                if (poliza.getFechaFin() == null) continue;

                // Construir mensaje descriptivo
                long diasRestantes = ChronoUnit.DAYS.between(hoy, poliza.getFechaFin());
                String estadoDesc = diasRestantes < 0 ? "VENCIDA" : "PRÓXIMA A VENCER";
                String mensaje = String.format(
                    "Alerta de Seguro: La póliza %s (%s - %s) está %s. Fecha fin: %s (%s días). Gestione la renovación oportuna bajo Ley 675.",
                    poliza.getNumeroPoliza(),
                    poliza.getCompaniaAseguradora(),
                    poliza.getRamoCobertura(),
                    estadoDesc,
                    poliza.getFechaFin(),
                    diasRestantes < 0 ? Math.abs(diasRestantes) + " días de vencimiento" : diasRestantes + " días restantes"
                );

                // Deduplicación atómica estructurada por ID_POLIZA (OBS-02):
                // Usamos MERGE atómico en Oracle para evitar race conditions concurrentes.
                // Si ya existe una alerta de tipo VENCIMIENTO_POLIZA para este ID_POLIZA en los últimos 15 días,
                // no se inserta fila (rowsAffected == 0) y se omite la notificación repetida.
                String mergeSql = "MERGE INTO ALERTAS_ADMIN a " +
                    "USING (SELECT ? AS id_prop, ? AS id_pol, 'VENCIMIENTO_POLIZA' AS tipo, ? AS msg FROM DUAL) src " +
                    "ON (a.ID_PROPIEDAD = src.id_prop AND a.TIPO_ALERTA = src.tipo AND a.ID_POLIZA = src.id_pol " +
                    "    AND a.FECHA_CREACION >= SYSTIMESTAMP - INTERVAL '15' DAY) " +
                    "WHEN NOT MATCHED THEN " +
                    "INSERT (ID_PROPIEDAD, TIPO_ALERTA, ID_POLIZA, MENSAJE, LEIDA) " +
                    "VALUES (src.id_prop, src.tipo, src.id_pol, src.msg, 'N')";

                int rowsAffected = jdbcTemplate.update(mergeSql, poliza.getIdPropiedad(), poliza.getIdPoliza(), mensaje);

                if (rowsAffected == 0) {
                    log.debug("[PolizaScheduler] Alerta omitida por deduplicación atómica estructurada para póliza ID {} ({})",
                        poliza.getIdPoliza(), poliza.getNumeroPoliza());
                    continue;
                }

                alertasGeneradas++;
                log.info("[PolizaScheduler] Generada alerta atómica para póliza ID {} ({}) en propiedad {}",
                    poliza.getIdPoliza(), poliza.getNumeroPoliza(), poliza.getIdPropiedad());

                // Despachar correo electrónico preventivo si existe administrador con email
                intentarNotificacionEmail(poliza, mensaje);
            }
        } catch (Exception e) {
            log.error("[PolizaScheduler] Error durante la verificación de vencimientos: {}", e.getMessage(), e);
        }

        log.info("[PolizaScheduler] Verificación completada. Total alertas generadas: {}", alertasGeneradas);
        return alertasGeneradas;
    }

    private void intentarNotificacionEmail(PolizaSeguroDTO poliza, String mensaje) {
        try {
            List<String> emails = jdbcTemplate.query(
                "SELECT u.EMAIL FROM USUARIOS u " +
                "JOIN USUARIO_ASIGNACIONES a ON u.ID_USUARIO = a.ID_USUARIO " +
                "JOIN ROLES r ON a.ID_ROL = r.ID_ROL " +
                "WHERE a.ID_PROPIEDAD = ? AND r.CODIGO = 'ADMIN_PROPIEDAD' AND u.ESTADO = 'ACTIVO' AND u.EMAIL IS NOT NULL",
                (rs, rowNum) -> rs.getString("EMAIL"),
                poliza.getIdPropiedad()
            );

            for (String email : emails) {
                if (email != null && !email.isBlank()) {
                    String asunto = "Alerta SAED: Renovación de Póliza " + poliza.getNumeroPoliza();
                    String html = String.format(
                        "<h2>Notificación de Vencimiento de Póliza</h2>" +
                        "<p>%s</p>" +
                        "<ul>" +
                        "<li><strong>Aseguradora:</strong> %s</li>" +
                        "<li><strong>Póliza Nº:</strong> %s</li>" +
                        "<li><strong>Ramo:</strong> %s</li>" +
                        "<li><strong>Vigencia Hasta:</strong> %s</li>" +
                        "<li><strong>Deducible:</strong> %s</li>" +
                        "</ul>" +
                        "<p>Ingrese al portal administrativo de SAED para gestionar la renovación o consultar el documento de cobertura.</p>",
                        mensaje,
                        poliza.getCompaniaAseguradora(),
                        poliza.getNumeroPoliza(),
                        poliza.getRamoCobertura(),
                        poliza.getFechaFin(),
                        poliza.getDeducible() != null ? poliza.getDeducible() : "No especificado"
                    );
                    emailService.enviarHtmlPublico(email, asunto, html);
                }
            }
        } catch (Exception e) {
            log.warn("[PolizaScheduler] No se pudo enviar correo de alerta para póliza {}: {}", poliza.getNumeroPoliza(), e.getMessage());
        }
    }
}
