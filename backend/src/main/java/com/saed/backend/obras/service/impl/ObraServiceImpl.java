package com.saed.backend.obras.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import com.saed.backend.obras.dto.IngresoObraDTO;
import com.saed.backend.obras.dto.InspeccionObraDTO;
import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.repository.ObraRepository;
import com.saed.backend.obras.service.ObraService;
import com.saed.backend.reservas.exception.UnidadEnMoraException;
import com.saed.backend.trabajadores.repository.ObraTrabajadorRepository;
import com.saed.backend.trabajadores.service.TrabajadorService;
import com.saed.backend.trabajadores.dto.ObraTrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
public class ObraServiceImpl implements ObraService {

    private final ObraRepository obraRepository;
    private final PazYSalvoService pazYSalvoService;
    private final TrabajadorService trabajadorService;
    private final ObraTrabajadorRepository obraTrabajadorRepository;

    public ObraServiceImpl(ObraRepository obraRepository, PazYSalvoService pazYSalvoService,
                           TrabajadorService trabajadorService, ObraTrabajadorRepository obraTrabajadorRepository) {
        this.obraRepository = obraRepository;
        this.pazYSalvoService = pazYSalvoService;
        this.trabajadorService = trabajadorService;
        this.obraTrabajadorRepository = obraTrabajadorRepository;
    }

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN_ORGANIZACION", "ADMIN_PROPIEDAD");
    private static final Set<String> GATE_ROLES = Set.of("ADMIN_ORGANIZACION", "ADMIN_PROPIEDAD", "PORTERO");
    private static final Set<String> RESULTADOS_ACEPTADOS = Set.of("APROBADA", "CON_OBSERVACIONES");
    private static final Set<String> RESULTADOS_INSPECCION = Set.of("APROBADA", "CON_OBSERVACIONES", "RECHAZADA");

    private static String currentRole() {
        return SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getRoleCode() : null;
    }

    private static Long requirePropertyId() {
        if (SaedContextHolder.getContext() == null || SaedContextHolder.getContext().getPropertyId() == null) {
            throw new AccessDeniedException("Operación rechazada: contexto sin copropiedad activa");
        }
        return SaedContextHolder.getContext().getPropertyId();
    }

    @Override
    public IngresoObraDTO verificarIngresoTrabajador(Long idObra, Long idTrabajador, LocalDate fecha) {
        if (!GATE_ROLES.contains(currentRole())) {
            throw new AccessDeniedException("La verificación de ingreso es exclusiva de portería y administración.");
        }
        ObraDTO obra = obraRepository.findById(idObra, requirePropertyId())
                .orElseThrow(() -> new NoSuchElementException("Obra no encontrada."));
        if (!"APROBADA".equals(obra.getEstado()) && !"EN_EJECUCION".equals(obra.getEstado())) {
            return new IngresoObraDTO(false, "La obra no está autorizada para ejecución (estado " + obra.getEstado() + ").", null, null);
        }
        if (fecha.isBefore(obra.getFechaInicio()) || fecha.isAfter(obra.getFechaFinEstimada())) {
            return new IngresoObraDTO(false, "La fecha está fuera de la ventana autorizada de la obra ("
                    + obra.getFechaInicio() + " a " + obra.getFechaFinEstimada() + ").", null, null);
        }
        Optional<ObraTrabajadorDTO> asignacion = obraTrabajadorRepository.buscarPorObraYTrabajador(idObra, idTrabajador);
        if (asignacion.isEmpty() || asignacion.get().trabajador() == null) {
            return new IngresoObraDTO(false, "El trabajador no está asignado a esta obra.", null, null);
        }
        TrabajadorDTO t = asignacion.get().trabajador();
        String nombre = t.nombreCompleto();
        String empresa = t.razonSocialProveedor() != null ? t.razonSocialProveedor() : t.empresaIndependiente();
        if (!"S".equals(asignacion.get().autorizado())) {
            return new IngresoObraDTO(false, "El trabajador no tiene autorización vigente de la administración.", nombre, empresa);
        }
        if (!"ACTIVO".equalsIgnoreCase(t.estado())) {
            return new IngresoObraDTO(false, "El trabajador está inactivo.", nombre, empresa);
        }
        if (!TrabajadorDTO.calcularArlVigente(t.arlFechaVencimiento(), t.arlFechaAfiliacion(), fecha)) {
            return new IngresoObraDTO(false, "La ARL del trabajador no está vigente.", nombre, empresa);
        }
        return new IngresoObraDTO(true, "Ingreso autorizado.", nombre, empresa);
    }

    @Override
    public List<ObraDTO> getObrasAdmin() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        return obraRepository.findAllByPropiedad(idPropiedad);
    }

    @Override
    public List<ObraDTO> getMisObras() {
        if (SaedContextHolder.getContext() == null || SaedContextHolder.getContext().getPropertyId() == null) {
            throw new AccessDeniedException("Operación rechazada: contexto sin copropiedad activa");
        }
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long idUnidad = SaedContextHolder.getContext().getUnitId();
        if (idUnidad == null) {
            throw new AccessDeniedException("Usuario no tiene unidad asignada");
        }
        return obraRepository.findAllByUnidad(idUnidad, idPropiedad);
    }

    @Override
    public ObraDTO getObraById(Long idObra) {
        if (SaedContextHolder.getContext() == null || SaedContextHolder.getContext().getPropertyId() == null) {
            throw new AccessDeniedException("Operación rechazada: contexto sin copropiedad activa");
        }
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        ObraDTO obra = obraRepository.findById(idObra, idPropiedad)
                .orElseThrow(() -> new NoSuchElementException("Obra no encontrada."));

        String roleCode = SaedContextHolder.getContext().getRoleCode();
        boolean isAdmin = "ADMIN_ORGANIZACION".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode);

        if (!isAdmin) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null || !userUnitId.equals(obra.getIdUnidad())) {
                // Another unit's work is indistinguishable from a missing one
                throw new NoSuchElementException("Obra no encontrada.");
            }
        }

        return obra;
    }

    @Override
    public Long solicitarObra(ObraDTO request) {
        if (SaedContextHolder.getContext() == null || SaedContextHolder.getContext().getPropertyId() == null) {
            throw new AccessDeniedException("Operación rechazada: contexto sin copropiedad activa");
        }
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long solicitadoPor = SaedContextHolder.getContext().getUserId();
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        boolean isAdmin = "ADMIN_ORGANIZACION".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode);

        if (!isAdmin) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null) {
                throw new AccessDeniedException("Usuario no tiene unidad asignada");
            }
            // Strict IDOR prevention: resident cannot submit obra for another unit
            if (request.getIdUnidad() != null && !request.getIdUnidad().equals(userUnitId)) {
                throw new AccessDeniedException("No tiene permisos para solicitar obras en otra unidad");
            }
            request.setIdUnidad(userUnitId);
        } else {
            if (request.getIdUnidad() == null) {
                throw new IllegalArgumentException("Se requiere especificar una unidad para la obra");
            }
        }

        if (request.getFechaInicio() == null || request.getFechaFinEstimada() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin estimada son obligatorias");
        }

        if (request.getFechaFinEstimada().isBefore(request.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio");
        }

        if (request.getDepositoGarantia() == null) {
            request.setDepositoGarantia(java.math.BigDecimal.ZERO);
        } else if (request.getDepositoGarantia().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El deposito de garantia no puede ser negativo");
        }

        // Validación Financiera Canónica Fase 6 (GAP-F9-04)
        validarPazYSalvoUnidad(request.getIdUnidad(), "solicitar");

        return obraRepository.createObra(request, idPropiedad, solicitadoPor);
    }

    @Override
    @Auditable(action = "APPROVE", resource = "OBRA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.HIGH)
    public void aprobarObra(Long idObra) {
        cambiarEstadoObra(idObra, "APROBADA");
    }

    @Override
    @Auditable(action = "START", resource = "OBRA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void iniciarObra(Long idObra) {
        cambiarEstadoObra(idObra, "EN_EJECUCION");
    }

    @Override
    @Auditable(action = "REJECT", resource = "OBRA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.HIGH)
    public void rechazarObra(Long idObra) {
        cambiarEstadoObra(idObra, "RECHAZADA");
    }

    @Override
    @Auditable(action = "FINALIZE", resource = "OBRA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void finalizarObra(Long idObra, InspeccionObraDTO inspeccion) {
        if (!ADMIN_ROLES.contains(currentRole())) {
            throw new AccessDeniedException("Solo la administración puede finalizar obras.");
        }
        if (inspeccion == null || inspeccion.resultado() == null) {
            throw new IllegalArgumentException("Finalizar una obra requiere el registro de la inspección final.");
        }
        String resultado = inspeccion.resultado().trim().toUpperCase();
        if (!RESULTADOS_INSPECCION.contains(resultado)) {
            throw new IllegalArgumentException("Resultado de inspección no válido. Permitidos: " + RESULTADOS_INSPECCION);
        }
        if (!RESULTADOS_ACEPTADOS.contains(resultado)) {
            throw new IllegalArgumentException("La inspección no aprobó la obra; corrija las observaciones antes de finalizarla.");
        }
        String observaciones = inspeccion.observaciones() != null ? inspeccion.observaciones().trim() : "";
        if (observaciones.length() < 5 || observaciones.length() > 1000) {
            throw new IllegalArgumentException("Las observaciones de la inspección son obligatorias (5 a 1000 caracteres).");
        }
        Long idPropiedad = requirePropertyId();
        ObraDTO obra = getObraById(idObra);
        String estado = obra.getEstado();
        if (!"EN_EJECUCION".equals(estado) && !"APROBADA".equals(estado) && !"SUSPENDIDA".equals(estado)) {
            throw new IllegalArgumentException("Solo se pueden finalizar obras activas (estado actual: " + estado + ")");
        }
        int updated = obraRepository.finalizarConInspeccion(idObra, idPropiedad, resultado, observaciones,
                SaedContextHolder.getContext().getUserId());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La obra cambió de estado; recargue e intente de nuevo.");
        }
    }

    @Override
    @Auditable(action = "UPDATE_STATUS", resource = "OBRA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void cambiarEstado(Long idObra, String nuevoEstado) {
        cambiarEstadoObra(idObra, nuevoEstado);
    }

    private void cambiarEstadoObra(Long idObra, String estado) {
        if (estado == null || estado.trim().isEmpty()) {
            throw new IllegalArgumentException("El nuevo estado es requerido");
        }
        String nuevoEstado = estado.trim().toUpperCase();
        if (!ADMIN_ROLES.contains(currentRole())) {
            throw new AccessDeniedException("Solo la administración puede cambiar el estado de una obra.");
        }

        if (SaedContextHolder.getContext() == null || SaedContextHolder.getContext().getPropertyId() == null) {
            throw new AccessDeniedException("Operación rechazada: contexto sin copropiedad activa");
        }
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long aprobadoPor = SaedContextHolder.getContext().getUserId();

        // Ensure obra exists and belongs to property
        ObraDTO obra = getObraById(idObra);
        String estadoActual = obra.getEstado();

        // Check if current is terminal
        if ("FINALIZADA".equals(estadoActual) || "RECHAZADA".equals(estadoActual)) {
            throw new IllegalArgumentException("No se puede modificar una obra en estado terminal (" + estadoActual + ")");
        }

        // State Machine validation
        switch (nuevoEstado) {
            case "APROBADA":
                if (!"SOLICITADA".equals(estadoActual)) {
                    throw new IllegalArgumentException("Solo se pueden aprobar obras en estado SOLICITADA (estado actual: " + estadoActual + ")");
                }
                validarPazYSalvoUnidad(obra.getIdUnidad(), "aprobar");
                break;

            case "EN_EJECUCION":
                if ("SOLICITADA".equals(estadoActual)) {
                    throw new IllegalArgumentException("No se puede iniciar una obra en estado SOLICITADA sin antes ser aprobada (bypass no permitido)");
                }
                if (!"APROBADA".equals(estadoActual) && !"SUSPENDIDA".equals(estadoActual)) {
                    throw new IllegalArgumentException("Solo se pueden iniciar obras en estado APROBADA o SUSPENDIDA (estado actual: " + estadoActual + ")");
                }
                validarPazYSalvoUnidad(obra.getIdUnidad(), "iniciar o reanudar");
                trabajadorService.validarTrabajadoresParaEjecucionObra(idObra);
                break;

            case "SUSPENDIDA":
                if (!"EN_EJECUCION".equals(estadoActual) && !"APROBADA".equals(estadoActual)) {
                    throw new IllegalArgumentException("Solo se pueden suspender obras en estado EN_EJECUCION o APROBADA (estado actual: " + estadoActual + ")");
                }
                break;

            case "FINALIZADA":
                // F09-03: completion only through finalizarObra with an inspection record
                throw new IllegalArgumentException("Para finalizar una obra registre la inspección final (POST /obras/{id}/finalizar).");

            case "RECHAZADA":
                if (!"SOLICITADA".equals(estadoActual) && !"APROBADA".equals(estadoActual) && !"SUSPENDIDA".equals(estadoActual)) {
                    throw new IllegalArgumentException("No se puede rechazar una obra en estado " + estadoActual);
                }
                break;

            default:
                throw new IllegalArgumentException("Estado no reconocido: " + nuevoEstado);
        }

        obraRepository.updateEstado(idObra, idPropiedad, nuevoEstado, aprobadoPor);
    }

    private void validarPazYSalvoUnidad(Long idUnidad, String accion) {
        PazYSalvoEstadoFinancieroDTO estadoFinanciero = pazYSalvoService.verificarEstadoFinanciero(idUnidad);
        if (!estadoFinanciero.pazYSalvo()) {
            String motivos = (estadoFinanciero.motivosBloqueo() != null && !estadoFinanciero.motivosBloqueo().isEmpty())
                    ? String.join("; ", estadoFinanciero.motivosBloqueo())
                    : "Saldo pendiente en cartera o multas";
            String identificador = (estadoFinanciero.identificadorUnidad() != null)
                    ? estadoFinanciero.identificadorUnidad()
                    : String.valueOf(idUnidad);
            throw new UnidadEnMoraException("No se puede " + accion + " la obra: la unidad "
                    + identificador + " presenta mora financiera: " + motivos);
        }
    }
}
