package com.saed.backend.proveedores.contratos;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

/**
 * Supplier contract renewal notices (F09-02). {@link #generarAvisos(Long, LocalDate)} is the entry point a
 * scheduler (or the generic automation engine, F12) calls per property. Each VIGENTE contract whose notice
 * window has opened gets exactly one notice per end date (period): the unique index UQ_AVISO_RENOV_PERIODO
 * guards replays, restarts and concurrent instances, and the admin alert is created only together with a
 * newly recorded notice, in the same transaction.
 */
@Service
public class AvisoRenovacionService {

    private static final Logger log = LoggerFactory.getLogger(AvisoRenovacionService.class);
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN_PROPIEDAD", "ADMIN_ORGANIZACION");

    private final AvisoRenovacionRepository repository;

    public AvisoRenovacionService(AvisoRenovacionRepository repository) {
        this.repository = repository;
    }

    /** True from {@code diasAlerta} days before {@code fechaFin} up to and including the end date. */
    public static boolean enVentanaDeAviso(LocalDate fechaFin, int diasAlerta, LocalDate fecha) {
        if (fechaFin == null || fecha == null) {
            return false;
        }
        return !fecha.isAfter(fechaFin) && !fecha.isBefore(fechaFin.minusDays(Math.max(0, diasAlerta)));
    }

    @Transactional
    public int generarAvisos(Long idPropiedad, LocalDate fecha) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || !ADMIN_ROLES.contains(ctx.getRoleCode())
                || idPropiedad == null || !idPropiedad.equals(ctx.getPropertyId())) {
            throw new AccessDeniedException("La generación de avisos está limitada a la propiedad de la sesión.");
        }
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha de referencia es obligatoria.");
        }
        int creados = 0;
        for (ContratoPorVencerDTO contrato : repository.contratosPorVencer(idPropiedad, fecha)) {
            int dias = contrato.diasAlerta() != null ? contrato.diasAlerta() : 30;
            if (!enVentanaDeAviso(contrato.fechaFin(), dias, fecha)) {
                continue;
            }
            String periodo = contrato.fechaFin().toString();
            if (repository.existeAviso(contrato.idContratoProveedor(), periodo)) {
                continue;
            }
            try {
                repository.registrarAviso(contrato, periodo);
            } catch (DuplicateKeyException e) {
                // Another instance recorded this period's notice first; it also created the alert.
                log.info("[AvisoRenovacion] Notice for contract {} period {} already recorded concurrently",
                        contrato.idContratoProveedor(), periodo);
                continue;
            }
            repository.crearAlertaAdmin(idPropiedad, "El contrato " + contrato.numeroContrato() + " con "
                    + contrato.proveedor() + " vence el " + contrato.fechaFin() + ". Gestione su renovación.");
            creados++;
        }
        return creados;
    }
}
