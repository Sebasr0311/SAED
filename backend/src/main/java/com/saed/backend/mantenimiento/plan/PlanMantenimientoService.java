package com.saed.backend.mantenimiento.plan;

import com.saed.backend.activos.repository.ActivoRepository;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.proveedores.repository.ProveedorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Recurring maintenance plans (F09-01). {@link #generarOrdenesProgramadas(Long, LocalDate)} is the entry
 * point a scheduler calls per property: for each active plan it creates the scheduled order of the latest
 * due period exactly once. Idempotency is enforced by the unique index UQ_MANT_PLAN_PERIODO, so replays,
 * restarts and several instances never duplicate an order.
 */
@Service
public class PlanMantenimientoService {

    private static final Logger log = LoggerFactory.getLogger(PlanMantenimientoService.class);
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN_PROPIEDAD", "ADMIN_ORGANIZACION");
    private static final Set<String> TIPOS = Set.of("PREVENTIVO", "PREDICTIVO", "LOCATIVO");
    private static final Set<String> PRIORIDADES = Set.of("BAJA", "MEDIA", "ALTA", "URGENTE");

    private final PlanMantenimientoRepository repository;
    private final ActivoRepository activoRepository;
    private final ProveedorRepository proveedorRepository;
    private final PropertyRepository propertyRepository;

    public PlanMantenimientoService(PlanMantenimientoRepository repository, ActivoRepository activoRepository,
                                    ProveedorRepository proveedorRepository, PropertyRepository propertyRepository) {
        this.repository = repository;
        this.activoRepository = activoRepository;
        this.proveedorRepository = proveedorRepository;
        this.propertyRepository = propertyRepository;
    }

    private static SaedContext requireAdminContext() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || !ADMIN_ROLES.contains(ctx.getRoleCode()) || ctx.getPropertyId() == null) {
            throw new AccessDeniedException("Operación reservada a la administración de la copropiedad activa.");
        }
        return ctx;
    }

    private Long organizationOf(SaedContext ctx) {
        if (ctx.getOrganizationId() != null) {
            return ctx.getOrganizationId();
        }
        return propertyRepository.findById(ctx.getPropertyId())
                .map(p -> p.getIdOrganizacion())
                .orElseThrow(() -> new AccessDeniedException("No se pudo resolver la organización de la propiedad activa."));
    }

    @Transactional
    public Long crearPlan(PlanMantenimientoDTO plan) {
        SaedContext ctx = requireAdminContext();
        Long idPropiedad = ctx.getPropertyId();
        if (plan == null) {
            throw new IllegalArgumentException("El plan es obligatorio.");
        }
        String titulo = plan.titulo() != null ? plan.titulo().trim() : "";
        if (titulo.isEmpty() || titulo.length() > 150) {
            throw new IllegalArgumentException("El título es obligatorio (máximo 150 caracteres).");
        }
        if (plan.descripcionTrabajo() == null || plan.descripcionTrabajo().isBlank()) {
            throw new IllegalArgumentException("La descripción del trabajo es obligatoria.");
        }
        if (plan.fechaInicio() == null) {
            throw new IllegalArgumentException("La fecha de inicio del plan es obligatoria.");
        }
        PlanFrecuencia frecuencia = PlanFrecuencia.parse(plan.frecuencia());
        String tipo = plan.tipoMantenimiento() != null ? plan.tipoMantenimiento().trim().toUpperCase(Locale.ROOT) : "PREVENTIVO";
        if (!TIPOS.contains(tipo)) {
            throw new IllegalArgumentException("Tipo de mantenimiento no válido para un plan: " + tipo);
        }
        String prioridad = plan.prioridad() != null ? plan.prioridad().trim().toUpperCase(Locale.ROOT) : "MEDIA";
        if (!PRIORIDADES.contains(prioridad)) {
            throw new IllegalArgumentException("Prioridad no válida: " + prioridad);
        }
        BigDecimal costo = plan.costoEstimado() != null ? plan.costoEstimado() : BigDecimal.ZERO;
        if (costo.signum() < 0) {
            throw new IllegalArgumentException("El costo estimado no puede ser negativo.");
        }
        if (plan.idActivo() != null && activoRepository.buscarPorId(plan.idActivo(), idPropiedad).isEmpty()) {
            throw new NoSuchElementException("Activo no encontrado.");
        }
        if (plan.idProveedorServicio() != null
                && proveedorRepository.buscarPorId(plan.idProveedorServicio(), organizationOf(ctx)).isEmpty()) {
            throw new NoSuchElementException("Proveedor no encontrado.");
        }
        PlanMantenimientoDTO normalizado = new PlanMantenimientoDTO(null, idPropiedad, plan.idActivo(), titulo,
                plan.descripcionTrabajo().trim(), tipo, prioridad, frecuencia.name(), plan.fechaInicio(), costo,
                plan.idProveedorServicio(), "S");
        return repository.crear(normalizado, idPropiedad, ctx.getUserId());
    }

    @Transactional(readOnly = true)
    public List<PlanMantenimientoDTO> listarPlanes() {
        return repository.listar(requireAdminContext().getPropertyId());
    }

    @Transactional
    public void desactivarPlan(Long idPlan) {
        SaedContext ctx = requireAdminContext();
        if (repository.desactivar(idPlan, ctx.getPropertyId()) == 0) {
            throw new NoSuchElementException("Plan de mantenimiento no encontrado.");
        }
    }

    /**
     * Creates the scheduled order of the latest due period of every active plan of {@code idPropiedad}.
     * The caller's tenant context must be that property. Returns the number of orders created.
     */
    @Transactional
    public int generarOrdenesProgramadas(Long idPropiedad, LocalDate fechaCorte) {
        SaedContext ctx = requireAdminContext();
        if (idPropiedad == null || !idPropiedad.equals(ctx.getPropertyId())) {
            throw new AccessDeniedException("La generación de órdenes está limitada a la propiedad de la sesión.");
        }
        if (fechaCorte == null) {
            throw new IllegalArgumentException("La fecha de corte es obligatoria.");
        }
        int creadas = 0;
        for (PlanMantenimientoDTO plan : repository.listarActivos(idPropiedad)) {
            Optional<LocalDate> ocurrencia = PlanFrecuencia.parse(plan.frecuencia())
                    .ultimaOcurrencia(plan.fechaInicio(), fechaCorte);
            if (ocurrencia.isEmpty()) {
                continue;
            }
            String periodo = ocurrencia.get().toString();
            if (repository.existeOrden(plan.idPlan(), periodo)) {
                continue;
            }
            try {
                repository.crearOrden(plan, ocurrencia.get(), periodo);
                creadas++;
            } catch (DuplicateKeyException e) {
                // Another instance created this period's order first: the unique index is the idempotency guard.
                log.info("[PlanMantenimiento] Order for plan {} period {} already created concurrently", plan.idPlan(), periodo);
            }
        }
        return creadas;
    }
}
