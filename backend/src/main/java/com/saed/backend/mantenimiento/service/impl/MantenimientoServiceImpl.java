package com.saed.backend.mantenimiento.service.impl;

import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.repository.ActivoRepository;
import com.saed.backend.audit.Auditable;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.mantenimiento.dto.MantenimientoCreateDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoEstadoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoReprogramarDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoUpdateDTO;
import com.saed.backend.mantenimiento.exception.MantenimientoConflictoActivoException;
import com.saed.backend.mantenimiento.exception.MantenimientoConflictoBloqueoException;
import com.saed.backend.mantenimiento.exception.MantenimientoEstadoInvalidoException;
import com.saed.backend.mantenimiento.exception.MantenimientoNoEncontradoException;
import com.saed.backend.mantenimiento.exception.ProveedorNoValidoException;
import com.saed.backend.mantenimiento.exception.TransicionMantenimientoInvalidaException;
import com.saed.backend.mantenimiento.repository.MantenimientoRepository;
import com.saed.backend.mantenimiento.service.MantenimientoService;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.repository.ProveedorRepository;
import com.saed.backend.reservas.repository.ReservasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MantenimientoServiceImpl implements MantenimientoService {

    private static final Logger log = LoggerFactory.getLogger(MantenimientoServiceImpl.class);

    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "PROGRAMADO", "EN_PROCESO", "COMPLETADO", "CANCELADO", "REPROGRAMADO"
    );

    private final MantenimientoRepository mantenimientoRepository;
    private final ActivoRepository activoRepository;
    private final ProveedorRepository proveedorRepository;
    private final ReservasRepository reservasRepository;

    public MantenimientoServiceImpl(MantenimientoRepository mantenimientoRepository,
                                  ActivoRepository activoRepository,
                                  ProveedorRepository proveedorRepository,
                                  ReservasRepository reservasRepository) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.activoRepository = activoRepository;
        this.proveedorRepository = proveedorRepository;
        this.reservasRepository = reservasRepository;
    }

    private Long getRequiredPropertyId() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        if (propId == null) {
            throw new AccessDeniedException("Operación no permitida: se requiere una propiedad activa en el contexto de seguridad.");
        }
        return propId;
    }

    private Long getOrganizationId() {
        SaedContext ctx = SaedContextHolder.getContext();
        return ctx != null ? ctx.getOrganizationId() : null;
    }

    private Long getUserId() {
        SaedContext ctx = SaedContextHolder.getContext();
        return ctx != null ? ctx.getUserId() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MantenimientoDTO> listar(String estado, String tipo, String prioridad, Long idActivo, String search) {
        Long propId = getRequiredPropertyId();
        return mantenimientoRepository.findAllByPropiedad(propId, estado, tipo, prioridad, idActivo, search);
    }

    @Override
    @Transactional(readOnly = true)
    public MantenimientoDTO obtenerPorId(Long id) {
        Long propId = getRequiredPropertyId();
        return mantenimientoRepository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new MantenimientoNoEncontradoException("Orden de mantenimiento no encontrada con ID: " + id));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "MANTENIMIENTOS")
    public MantenimientoDTO crear(MantenimientoCreateDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("La solicitud de creación de mantenimiento no puede ser nula.");
        }
        Long propId = getRequiredPropertyId();
        Long orgId = getOrganizationId();
        Long userId = getUserId();

        // 1. Validaciones financieras
        if (dto.getCostoEstimado() != null && dto.getCostoEstimado().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El costo estimado no puede ser negativo.");
        }

        // 2. Validación de Activo (GAP-F9-02)
        if (dto.getIdActivo() != null) {
            ActivoDTO activo = activoRepository.buscarPorId(dto.getIdActivo(), propId)
                    .orElseThrow(() -> new MantenimientoConflictoActivoException("El activo no pertenece a la copropiedad actual o no existe."));

            if ("DADO_DE_BAJA".equalsIgnoreCase(activo.estado())) {
                throw new MantenimientoConflictoActivoException("No se puede programar mantenimiento para un activo que está dado de baja.");
            }
        }

        // 3. Validación de Proveedor (GAP-F9-01)
        if (dto.getIdProveedorServicio() != null) {
            validarProveedor(dto.getIdProveedorServicio(), orgId);
        }

        // 4. Validación de Bloqueo de Zona Común (Fase 8)
        if (dto.getIdZonaBloqueo() != null) {
            validarZonaParaBloqueo(dto.getIdZonaBloqueo(), propId);

            if (dto.getFechaInicioBloqueo() == null || dto.getFechaFinBloqueo() == null) {
                throw new IllegalArgumentException("Debe especificar la fecha de inicio y fin para el bloqueo de la zona común.");
            }
            if (!dto.getFechaFinBloqueo().isAfter(dto.getFechaInicioBloqueo())) {
                throw new IllegalArgumentException("La fecha de finalización del bloqueo debe ser posterior a la de inicio.");
            }

            // Detección estricta de solapamiento en BLOQUEOS_ZONA
            boolean haySolapamiento = mantenimientoRepository.hasOverlappingBloqueo(
                    dto.getIdZonaBloqueo(), dto.getFechaInicioBloqueo(), dto.getFechaFinBloqueo(), null
            );
            if (haySolapamiento) {
                throw new MantenimientoConflictoBloqueoException("La zona común ya cuenta con un bloqueo activo que se solapa con el intervalo solicitado.");
            }
        }

        // 5. Persistencia del Mantenimiento
        Long idMantenimiento = mantenimientoRepository.create(dto, propId, userId);
        if (idMantenimiento == null) {
            throw new IllegalStateException("Error al persistir la orden de mantenimiento.");
        }

        // 6. Persistencia del Bloqueo de Zona si fue solicitado
        if (dto.getIdZonaBloqueo() != null) {
            String motivo = dto.getMotivoBloqueo() != null && !dto.getMotivoBloqueo().isBlank()
                    ? dto.getMotivoBloqueo().trim()
                    : "Bloqueo por mantenimiento: " + dto.getTitulo();

            mantenimientoRepository.createBloqueoZona(
                    dto.getIdZonaBloqueo(),
                    propId,
                    idMantenimiento,
                    motivo,
                    dto.getFechaInicioBloqueo(),
                    dto.getFechaFinBloqueo(),
                    userId
            );
            log.info("[Mantenimiento] Creado bloqueo en zona {} vinculado a mantenimiento {}", dto.getIdZonaBloqueo(), idMantenimiento);
        }

        log.info("[Mantenimiento] Creado mantenimiento #{} '{}' en propiedad {}", idMantenimiento, dto.getTitulo(), propId);
        return obtenerPorId(idMantenimiento);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", resource = "MANTENIMIENTOS")
    public MantenimientoDTO actualizar(Long id, MantenimientoUpdateDTO dto) {
        if (id == null || dto == null) {
            throw new IllegalArgumentException("ID y datos de actualización no pueden ser nulos.");
        }
        Long propId = getRequiredPropertyId();
        Long orgId = getOrganizationId();

        mantenimientoRepository.lockMantenimientoForUpdate(id, propId);

        MantenimientoDTO existing = mantenimientoRepository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new MantenimientoNoEncontradoException("Orden de mantenimiento no encontrada con ID: " + id));

        // Solo se permite editar en estados no terminales
        if ("COMPLETADO".equalsIgnoreCase(existing.getEstado()) || "CANCELADO".equalsIgnoreCase(existing.getEstado())) {
            throw new TransicionMantenimientoInvalidaException(
                    "No se puede modificar una orden de mantenimiento que ya se encuentra en estado " + existing.getEstado() + "."
            );
        }

        if (dto.getCostoEstimado() != null && dto.getCostoEstimado().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El costo estimado no puede ser negativo.");
        }

        if (dto.getIdActivo() != null) {
            ActivoDTO activo = activoRepository.buscarPorId(dto.getIdActivo(), propId)
                    .orElseThrow(() -> new MantenimientoConflictoActivoException("El activo no pertenece a la copropiedad actual."));
            if ("DADO_DE_BAJA".equalsIgnoreCase(activo.estado())) {
                throw new MantenimientoConflictoActivoException("No se puede asignar un activo que está dado de baja.");
            }
        }

        if (dto.getIdProveedorServicio() != null) {
            validarProveedor(dto.getIdProveedorServicio(), orgId);
        }

        mantenimientoRepository.update(id, propId, dto);
        log.info("[Mantenimiento] Actualizado mantenimiento #{} en propiedad {}", id, propId);
        return obtenerPorId(id);
    }

    @Override
    @Transactional
    @Auditable(action = "CHANGE_STATUS", resource = "MANTENIMIENTOS")
    public MantenimientoDTO cambiarEstado(Long id, MantenimientoEstadoDTO dto) {
        if (id == null || dto == null || dto.getNuevoEstado() == null) {
            throw new IllegalArgumentException("ID y nuevo estado son obligatorios.");
        }
        Long propId = getRequiredPropertyId();
        String targetEstado = dto.getNuevoEstado().trim().toUpperCase();

        if (!ESTADOS_VALIDOS.contains(targetEstado)) {
            throw new MantenimientoEstadoInvalidoException("El estado '" + targetEstado + "' no es un estado válido de mantenimiento.");
        }

        mantenimientoRepository.lockMantenimientoForUpdate(id, propId);

        MantenimientoDTO existing = mantenimientoRepository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new MantenimientoNoEncontradoException("Orden de mantenimiento no encontrada con ID: " + id));

        String currentEstado = existing.getEstado().toUpperCase().trim();

        if (currentEstado.equals(targetEstado)) {
            throw new TransicionMantenimientoInvalidaException("El mantenimiento ya se encuentra en estado " + currentEstado + ".");
        }

        // Validación de máquina de estados estricta
        if ("COMPLETADO".equals(currentEstado) || "CANCELADO".equals(currentEstado)) {
            throw new TransicionMantenimientoInvalidaException(
                    "No se permiten transiciones desde el estado terminal '" + currentEstado + "'."
            );
        }

        switch (targetEstado) {
            case "EN_PROCESO":
                if (!"PROGRAMADO".equals(currentEstado) && !"REPROGRAMADO".equals(currentEstado)) {
                    throw new TransicionMantenimientoInvalidaException(
                            "Solo se puede pasar a EN_PROCESO desde PROGRAMADO o REPROGRAMADO (estado actual: " + currentEstado + ")."
                    );
                }
                // Sincronización con ACTIVOS: Bloqueo pesimista y actualización a MANTENIMIENTO
                if (existing.getIdActivo() != null) {
                    mantenimientoRepository.lockActivoForUpdate(existing.getIdActivo(), propId);
                    String estadoActivo = mantenimientoRepository.getEstadoActivo(existing.getIdActivo(), propId).orElse("OPERATIVO");

                    if ("DADO_DE_BAJA".equalsIgnoreCase(estadoActivo)) {
                        throw new MantenimientoConflictoActivoException("El activo se encuentra dado de baja y no puede ingresar a mantenimiento.");
                    }

                    boolean activoConOtroMantenimiento = mantenimientoRepository.hasActiveMaintenanceOnActivo(existing.getIdActivo(), propId, id);
                    if (activoConOtroMantenimiento && "MANTENIMIENTO".equalsIgnoreCase(estadoActivo)) {
                        throw new MantenimientoConflictoActivoException("El activo ya se encuentra en mantenimiento por otra orden activa.");
                    }

                    mantenimientoRepository.updateEstadoActivo(existing.getIdActivo(), propId, "MANTENIMIENTO");
                    log.info("[Mantenimiento] Activo #{} transicionado a MANTENIMIENTO", existing.getIdActivo());
                }
                break;

            case "COMPLETADO":
                if (!"EN_PROCESO".equals(currentEstado)) {
                    throw new TransicionMantenimientoInvalidaException(
                            "Solo se puede COMPLETAR un mantenimiento que se encuentre EN_PROCESO (estado actual: " + currentEstado + ")."
                    );
                }
                if (dto.getCostoReal() != null && dto.getCostoReal().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("El costo real no puede ser negativo.");
                }

                // Sincronización con ACTIVOS: Revertir a OPERATIVO
                if (existing.getIdActivo() != null) {
                    mantenimientoRepository.lockActivoForUpdate(existing.getIdActivo(), propId);
                    mantenimientoRepository.updateEstadoActivo(existing.getIdActivo(), propId, "OPERATIVO");
                    log.info("[Mantenimiento] Activo #{} revertido a OPERATIVO tras completar mantenimiento", existing.getIdActivo());
                }

                // Liberación de bloqueo de zona si existía
                mantenimientoRepository.deleteBloqueoByMantenimiento(id, propId);
                break;

            case "CANCELADO":
                // Si el activo estaba en MANTENIMIENTO por esta orden, revertir a OPERATIVO
                if (existing.getIdActivo() != null && "EN_PROCESO".equals(currentEstado)) {
                    mantenimientoRepository.lockActivoForUpdate(existing.getIdActivo(), propId);
                    mantenimientoRepository.updateEstadoActivo(existing.getIdActivo(), propId, "OPERATIVO");
                    log.info("[Mantenimiento] Activo #{} revertido a OPERATIVO tras cancelar orden en proceso", existing.getIdActivo());
                }

                // Liberación de bloqueo de zona si existía
                mantenimientoRepository.deleteBloqueoByMantenimiento(id, propId);
                break;

            case "REPROGRAMADO":
                if (!"PROGRAMADO".equals(currentEstado) && !"REPROGRAMADO".equals(currentEstado)) {
                    throw new TransicionMantenimientoInvalidaException(
                            "Solo se puede reprogramar desde PROGRAMADO o REPROGRAMADO (estado actual: " + currentEstado + ")."
                    );
                }
                break;

            default:
                throw new TransicionMantenimientoInvalidaException("Transición a '" + targetEstado + "' no permitida.");
        }

        BigDecimal costoFinal = dto.getCostoReal();
        if (costoFinal == null && "COMPLETADO".equals(targetEstado)) {
            costoFinal = existing.getCostoEstimado() != null ? existing.getCostoEstimado() : BigDecimal.ZERO;
        }

        mantenimientoRepository.updateEstado(
                id,
                propId,
                targetEstado,
                costoFinal,
                dto.getFechaEjecucion(),
                dto.getNotasCierre(),
                dto.getInformeTecnicoUrl(),
                dto.getEvidenciaDespuesUrl()
        );

        log.info("[Mantenimiento] Mantenimiento #{} cambió de estado '{}' a '{}'", id, currentEstado, targetEstado);
        return obtenerPorId(id);
    }

    @Override
    @Transactional
    @Auditable(action = "RESCHEDULE", resource = "MANTENIMIENTOS")
    public MantenimientoDTO reprogramar(Long id, MantenimientoReprogramarDTO dto) {
        if (id == null || dto == null || dto.getNuevaFechaProgramada() == null) {
            throw new IllegalArgumentException("ID y nueva fecha programada son obligatorios.");
        }
        Long propId = getRequiredPropertyId();

        mantenimientoRepository.lockMantenimientoForUpdate(id, propId);

        MantenimientoDTO existing = mantenimientoRepository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new MantenimientoNoEncontradoException("Orden de mantenimiento no encontrada con ID: " + id));

        String currentEstado = existing.getEstado().toUpperCase().trim();
        if ("COMPLETADO".equals(currentEstado) || "CANCELADO".equals(currentEstado)) {
            throw new TransicionMantenimientoInvalidaException(
                    "No se puede reprogramar un mantenimiento en estado " + currentEstado + "."
            );
        }

        // Si tiene bloqueo de zona asociado y se enviaron nuevas fechas de bloqueo
        if (existing.getIdBloqueoZona() != null && dto.getNuevaFechaInicioBloqueo() != null && dto.getNuevaFechaFinBloqueo() != null) {
            if (!dto.getNuevaFechaFinBloqueo().isAfter(dto.getNuevaFechaInicioBloqueo())) {
                throw new IllegalArgumentException("La nueva fecha de fin de bloqueo debe ser posterior a la de inicio.");
            }

            boolean haySolapamiento = mantenimientoRepository.hasOverlappingBloqueo(
                    existing.getIdZonaBloqueada(),
                    dto.getNuevaFechaInicioBloqueo(),
                    dto.getNuevaFechaFinBloqueo(),
                    id
            );
            if (haySolapamiento) {
                throw new MantenimientoConflictoBloqueoException("La nueva fecha de bloqueo se solapa con otro bloqueo activo en la zona común.");
            }

            mantenimientoRepository.updateBloqueoFechas(
                    id, propId, dto.getNuevaFechaInicioBloqueo(), dto.getNuevaFechaFinBloqueo()
            );
        }

        mantenimientoRepository.reprogramar(id, propId, dto.getNuevaFechaProgramada(), dto.getMotivo());
        log.info("[Mantenimiento] Mantenimiento #{} reprogramado para {}", id, dto.getNuevaFechaProgramada());
        return obtenerPorId(id);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", resource = "MANTENIMIENTOS")
    public MantenimientoDTO cancelar(Long id, String motivo) {
        MantenimientoEstadoDTO estadoDTO = new MantenimientoEstadoDTO();
        estadoDTO.setNuevoEstado("CANCELADO");
        estadoDTO.setNotasCierre(motivo);
        return cambiarEstado(id, estadoDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerKpis() {
        Long propId = getRequiredPropertyId();
        return mantenimientoRepository.getKpis(propId);
    }

    private void validarProveedor(Long idProveedor, Long orgId) {
        ProveedorDTO prov = proveedorRepository.buscarPorIdDirecto(idProveedor)
                .orElseThrow(() -> new ProveedorNoValidoException("El proveedor de servicios especificado no existe."));

        if (orgId != null && prov.idOrganizacion() != null && !orgId.equals(prov.idOrganizacion())) {
            throw new ProveedorNoValidoException("El proveedor de servicios no pertenece a la organización actual.");
        }

        if (!"ACTIVO".equalsIgnoreCase(prov.estado())) {
            throw new ProveedorNoValidoException(
                    "El proveedor de servicios '" + prov.razonSocial() + "' no se encuentra activo (estado: " + prov.estado() + ")."
            );
        }
    }

    private void validarZonaParaBloqueo(Long idZona, Long propId) {
        if (!reservasRepository.existsZonaInPropiedad(idZona, propId)) {
            throw new MantenimientoConflictoBloqueoException("La zona común especificada no pertenece a la copropiedad actual o no existe.");
        }
    }
}
