package com.saed.backend.obras.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.repository.ObraRepository;
import com.saed.backend.obras.service.ObraService;
import com.saed.backend.reservas.exception.UnidadEnMoraException;
import com.saed.backend.trabajadores.service.TrabajadorService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ObraServiceImpl implements ObraService {

    private final ObraRepository obraRepository;
    private final PazYSalvoService pazYSalvoService;
    private final TrabajadorService trabajadorService;

    public ObraServiceImpl(ObraRepository obraRepository, PazYSalvoService pazYSalvoService, TrabajadorService trabajadorService) {
        this.obraRepository = obraRepository;
        this.pazYSalvoService = pazYSalvoService;
        this.trabajadorService = trabajadorService;
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
                .orElseThrow(() -> new IllegalArgumentException("Obra no encontrada o acceso denegado"));
                
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        boolean isAdmin = "ADMIN_ORGANIZACION".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode);
        
        if (!isAdmin) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null || !userUnitId.equals(obra.getIdUnidad())) {
                throw new AccessDeniedException("Acceso denegado: no tiene permisos para ver esta obra");
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
    public void finalizarObra(Long idObra) {
        cambiarEstadoObra(idObra, "FINALIZADA");
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
                if (!"EN_EJECUCION".equals(estadoActual) && !"APROBADA".equals(estadoActual) && !"SUSPENDIDA".equals(estadoActual)) {
                    throw new IllegalArgumentException("Solo se pueden finalizar obras activas (estado actual: " + estadoActual + ")");
                }
                break;

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
