package com.saed.backend.reservas.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import com.saed.backend.reservas.dto.CreateZonaComunDTO;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.UpdateZonaComunDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import com.saed.backend.reservas.exception.AforoExcedidoException;
import com.saed.backend.reservas.exception.AsistentesInvalidosException;
import com.saed.backend.reservas.exception.ReservaEstadoInvalidoException;
import com.saed.backend.reservas.exception.ReservaSolapadaException;
import com.saed.backend.reservas.exception.ReservaTransicionInvalidaException;
import com.saed.backend.reservas.exception.UnidadEnMoraException;
import com.saed.backend.reservas.exception.ZonaNoDisponibleException;
import com.saed.backend.reservas.exception.ZonaNombreDuplicadoException;
import com.saed.backend.reservas.repository.ReservasRepository;
import com.saed.backend.reservas.service.ReservasService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class ReservasServiceImpl implements ReservasService {

    private final ReservasRepository reservasRepository;
    private final PazYSalvoService pazYSalvoService;

    public ReservasServiceImpl(ReservasRepository reservasRepository, PazYSalvoService pazYSalvoService) {
        this.reservasRepository = reservasRepository;
        this.pazYSalvoService = pazYSalvoService;
    }

    @Override
    public List<ZonaComunDTO> getAllZonasComunes() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            return reservasRepository.findAllZonas();
        }
        if (idPropiedad != null) {
            return reservasRepository.findZonasByPropiedad(idPropiedad);
        }
        return reservasRepository.findAllZonas();
    }

    @Override
    public ZonaComunDTO getZonaComunById(Long idZona) {
        if (idZona == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID de la zona común no puede ser nulo.");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("SUPERADMIN no tiene acceso operacional a zonas comunes.");
        }
        if (idPropiedad == null) {
            throw new AccessDeniedException("Contexto de propiedad no válido.");
        }

        return reservasRepository.findZonaByIdAndPropiedad(idZona, idPropiedad)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona común no encontrada."));
    }

    @Override
    @Transactional
    public ZonaComunDTO createZonaComun(CreateZonaComunDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud de creación no puede ser nula.");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("SUPERADMIN no tiene permisos para crear zonas comunes.");
        }
        if (idPropiedad == null) {
            throw new AccessDeniedException("Contexto de propiedad no válido para creación de zonas.");
        }

        // Validación preventiva de nombre duplicado en la misma propiedad (UX)
        if (reservasRepository.existsZonaNombreInPropiedad(dto.getNombre().trim(), idPropiedad, null)) {
            throw new ZonaNombreDuplicadoException(
                "Ya existe una zona común con el nombre '" + dto.getNombre().trim() + "' en esta propiedad."
            );
        }

        Long idZona = reservasRepository.saveZona(dto, idPropiedad);
        if (idZona == null) {
            throw new IllegalStateException("Error al persistir la nueva zona común.");
        }

        return reservasRepository.findZonaByIdAndPropiedad(idZona, idPropiedad)
                .orElseThrow(() -> new IllegalStateException("Zona común creada pero no pudo ser recuperada."));
    }

    @Override
    @Transactional
    public ZonaComunDTO updateZonaComun(Long idZona, UpdateZonaComunDTO dto) {
        if (idZona == null || dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parámetros de actualización inválidos.");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("SUPERADMIN no tiene permisos para actualizar zonas comunes.");
        }
        if (idPropiedad == null) {
            throw new AccessDeniedException("Contexto de propiedad no válido.");
        }

        // 1. Verificar existencia y pertenencia a la copropiedad (Anti-IDOR)
        ZonaComunDTO existing = reservasRepository.findZonaByIdAndPropiedad(idZona, idPropiedad)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona común no encontrada."));

        // 2. Validación preventiva de nombre duplicado si se modifica el nombre
        if (dto.getNombre() != null && !dto.getNombre().trim().equalsIgnoreCase(existing.getNombre())) {
            if (reservasRepository.existsZonaNombreInPropiedad(dto.getNombre().trim(), idPropiedad, idZona)) {
                throw new ZonaNombreDuplicadoException(
                    "Ya existe una zona común con el nombre '" + dto.getNombre().trim() + "' en esta propiedad."
                );
            }
        }

        // 3. Actualizar
        boolean updated = reservasRepository.updateZona(idZona, idPropiedad, dto);
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona común no encontrada.");
        }

        return reservasRepository.findZonaByIdAndPropiedad(idZona, idPropiedad)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona común no encontrada tras actualización."));
    }

    @Override
    @Transactional
    public void deleteZonaComun(Long idZona) {
        if (idZona == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID de la zona no puede ser nulo.");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("SUPERADMIN no tiene permisos para eliminar zonas comunes.");
        }
        if (idPropiedad == null) {
            throw new AccessDeniedException("Contexto de propiedad no válido.");
        }

        // Verificar existencia en la propiedad (Anti-IDOR)
        reservasRepository.findZonaByIdAndPropiedad(idZona, idPropiedad)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona común no encontrada."));

        // Baja lógica obligatoria: UPDATE ZONAS_COMUNES SET ESTADO = 'INACTIVA'
        boolean deleted = reservasRepository.softDeleteZona(idZona, idPropiedad);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona común no encontrada.");
        }
    }

    @Override
    public List<ReservaDTO> getAllReservas() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            return reservasRepository.findAllReservas();
        }
        if (idPropiedad != null) {
            return reservasRepository.findReservasByPropiedad(idPropiedad);
        }
        return reservasRepository.findAllReservas();
    }

    @Override
    public List<ReservaDTO> getMyReservas() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long idUsuario = ctx != null ? ctx.getUserId() : null;
        Long unitId = ctx != null ? ctx.getUnitId() : null;
        if (unitId != null) {
            return reservasRepository.findReservasByUnidad(unitId);
        }
        return reservasRepository.findReservasByPersona(idUsuario);
    }

    @Override
    public ReservaDTO getReservaById(Long idReserva) {
        if (idReserva == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID de reserva no puede ser nulo.");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;
        Long contextUnitId = ctx != null ? ctx.getUnitId() : null;

        ReservaDTO reserva = reservasRepository.findReservaById(idReserva)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada."));

        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            return reserva;
        }

        // Validación de copropiedad (Cross-Property)
        if (idPropiedad != null && !reservasRepository.existsZonaInPropiedad(reserva.getIdZona(), idPropiedad)) {
            throw new AccessDeniedException("No tiene permisos para acceder a reservas de otra copropiedad.");
        }

        // Si es RESIDENTE o RESIDENTE_CONVIVENCIA, solo puede consultar reservas de su propia unidad
        if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            if (contextUnitId == null || !contextUnitId.equals(reserva.getIdUnidad())) {
                throw new AccessDeniedException("No tiene permisos para acceder a reservas de otra unidad.");
            }
        }

        return reserva;
    }

    @Override
    @Transactional
    public Long createReserva(ReservaDTO reserva) {
        if (reserva == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud de reserva no puede ser nula.");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long idUsuario = ctx != null ? ctx.getUserId() : null;
        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        if (idUsuario == null || idPropiedad == null) {
            throw new IllegalStateException("Contexto de usuario inválido.");
        }

        // 1. Resolución segura de unidad y validación Anti-IDOR
        Long unitIdEfectiva;
        if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            Long contextUnitId = ctx.getUnitId();
            if (contextUnitId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El residente no tiene una unidad asignada en su contexto.");
            }
            // Anti-IDOR: Si el cliente envía un idUnidad en el payload, debe coincidir exactamente con el contexto
            if (reserva.getIdUnidad() != null && !reserva.getIdUnidad().equals(contextUnitId)) {
                throw new AccessDeniedException("No tiene permisos para realizar reservas en nombre de otra unidad.");
            }
            unitIdEfectiva = contextUnitId;
        } else {
            // ADMIN_PROPIEDAD u otros roles autorizados
            if (reserva.getIdUnidad() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe especificar la unidad para la cual se realiza la reserva.");
            }
            unitIdEfectiva = reserva.getIdUnidad();
        }

        // Validación Cross-Property de unidad: la unidad debe pertenecer a la copropiedad del contexto
        if (!reservasRepository.existsUnidadInPropiedad(unitIdEfectiva, idPropiedad)) {
            throw new AccessDeniedException("La unidad especificada no pertenece a la copropiedad actual o no existe.");
        }

        reserva.setIdUnidad(unitIdEfectiva);
        reserva.setIdPersonaSolicita(idUsuario);

        // 2. Validación Cross-Property y Estado Operativo de Zona Común (GAP-F8-05)
        if (reserva.getIdZona() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe especificar la zona común a reservar.");
        }
        ZonaComunDTO zona = reservasRepository.findZonaByIdAndPropiedad(reserva.getIdZona(), idPropiedad)
                .orElseThrow(() -> new AccessDeniedException("La zona común no pertenece a la copropiedad actual o no existe."));

        if (!"ACTIVA".equalsIgnoreCase(zona.getEstado())) {
            throw new ZonaNoDisponibleException(
                "La zona común '" + zona.getNombre() + "' no se encuentra disponible para reservas (estado actual: " + zona.getEstado() + ")."
            );
        }

        // 3. GAP-F8-02: Validación canónica de estado financiero y bloqueo automático por mora con F6
        PazYSalvoEstadoFinancieroDTO estadoFinanciero = pazYSalvoService.verificarEstadoFinanciero(unitIdEfectiva);
        if (!estadoFinanciero.pazYSalvo() || (estadoFinanciero.saldoTotalExigible() != null && estadoFinanciero.saldoTotalExigible().compareTo(BigDecimal.ZERO) > 0)) {
            throw new UnidadEnMoraException(
                "La unidad presenta obligaciones financieras pendientes y no puede realizar reservas de zonas comunes."
            );
        }

        // 4. Parámetros por defecto y validación estricta de intervalo horario
        if (reserva.getFechaReserva() == null) {
            reserva.setFechaReserva(LocalDate.now().plusDays(1));
        }
        if (reserva.getHoraInicio() == null) {
            reserva.setHoraInicio("10:00");
        }
        if (reserva.getHoraFin() == null) {
            reserva.setHoraFin("12:00");
        }
        if (reserva.getHoraInicio().compareTo(reserva.getHoraFin()) >= 0) {
            throw new IllegalArgumentException("La hora de inicio debe ser anterior a la hora de finalización.");
        }
        // GAP-F8-07-02: Validación semántica de asistentes (> 0)
        if (reserva.getCantidadAsistentes() != null && reserva.getCantidadAsistentes() <= 0) {
            throw new AsistentesInvalidosException("La cantidad de asistentes debe ser un número entero mayor a cero.");
        }
        if (reserva.getCantidadAsistentes() == null) {
            reserva.setCantidadAsistentes(1);
        }

        // GAP-F8-07-01: Validación de Aforo Máximo contra la zona común
        if (zona.getAforoMaximo() != null && zona.getAforoMaximo() > 0 && reserva.getCantidadAsistentes() > zona.getAforoMaximo()) {
            throw new AforoExcedidoException(
                "La cantidad de asistentes (" + reserva.getCantidadAsistentes() + 
                ") excede el aforo máximo permitido para la zona '" + zona.getNombre() + 
                "' (" + zona.getAforoMaximo() + " personas)."
            );
        }

        // GAP-F8-07-03: Herencia de costo de la zona (el servidor es la fuente autoritativa del precio)
        if (zona.getCostoReserva() != null && zona.getCostoReserva().compareTo(BigDecimal.ZERO) > 0) {
            reserva.setCostoTotal(zona.getCostoReserva());
        } else {
            reserva.setCostoTotal(BigDecimal.ZERO);
        }

        // 5. GAP-F8-03: Bloqueo pesimista a nivel de fila de zona en Oracle para serialización concurrente
        reservasRepository.lockZonaForUpdate(reserva.getIdZona(), idPropiedad);

        // 6. GAP-F8-03: Detección estricta de solapamiento horario en la misma fecha y zona común
        boolean tieneSolapamiento = reservasRepository.hasOverlappingReserva(
                reserva.getIdZona(),
                reserva.getFechaReserva(),
                reserva.getHoraInicio(),
                reserva.getHoraFin()
        );
        if (tieneSolapamiento) {
            throw new ReservaSolapadaException(
                "La zona común ya cuenta con una reserva activa que se solapa con el horario solicitado."
            );
        }

        // 6b. GAP-F9-03: Detección estricta de solapamiento con BLOQUEOS_ZONA por mantenimiento
        boolean tieneBloqueo = reservasRepository.hasOverlappingBloqueo(
                reserva.getIdZona(),
                reserva.getFechaReserva(),
                reserva.getHoraInicio(),
                reserva.getHoraFin()
        );
        if (tieneBloqueo) {
            throw new ReservaSolapadaException(
                "La zona común se encuentra bloqueada por mantenimiento en el horario solicitado."
            );
        }

        reserva.setEstado("PENDIENTE");
        return reservasRepository.createReserva(reserva, idPropiedad);
    }

    private static final Set<String> ESTADOS_VALIDOS = Set.of(
        "PENDIENTE", "APROBADA", "RECHAZADA", "CANCELADA", "COMPLETADA"
    );

    @Override
    @Transactional
    public void updateReservaStatus(Long idReserva, String estado) {
        if (idReserva == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID de reserva no puede ser nulo.");
        }
        if (estado == null || !ESTADOS_VALIDOS.contains(estado.trim().toUpperCase())) {
            throw new ReservaEstadoInvalidoException("El estado '" + estado + "' no es un estado válido de reserva.");
        }
        String targetEstado = estado.trim().toUpperCase();

        SaedContext ctx = SaedContextHolder.getContext();
        Long idPersona = ctx != null ? ctx.getUserId() : null;

        // 1. Validar que la reserva existe y pertenece al tenant/propiedad (Anti-IDOR)
        getReservaById(idReserva); 

        // 2. Bloqueo pesimista de fila de reserva para serialización concurrente (GAP-F8-07-05)
        reservasRepository.lockReservaForUpdate(idReserva);
        ReservaDTO freshReserva = reservasRepository.findReservaById(idReserva)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada tras bloqueo."));

        String estadoActual = freshReserva.getEstado();

        // 3. Validación de máquina de estados estricta (GAP-F8-07-05)
        validarTransicionEstado(estadoActual, targetEstado);

        Long aprobadoPor = null;
        if ("APROBADA".equals(targetEstado) || "RECHAZADA".equals(targetEstado)) {
            aprobadoPor = idPersona;
        }
        reservasRepository.updateEstadoReserva(idReserva, targetEstado, aprobadoPor);
    }

    @Override
    @Transactional
    public void cancelReserva(Long idReserva) {
        if (idReserva == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID de reserva no puede ser nulo.");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx != null ? ctx.getRoleCode() : null;
        Long contextUnitId = ctx != null ? ctx.getUnitId() : null;

        // 1. Obtener la reserva y validar acceso contextual
        ReservaDTO reserva = getReservaById(idReserva);

        // GAP-F8-07-04: Si es residente o conviviente, confinar estrictamente a su unidad asignada
        if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            if (contextUnitId == null || !contextUnitId.equals(reserva.getIdUnidad())) {
                throw new AccessDeniedException("No tiene permisos para cancelar reservas de otra unidad.");
            }
        }

        // 2. Bloqueo pesimista de la fila de reserva
        reservasRepository.lockReservaForUpdate(idReserva);
        ReservaDTO freshReserva = reservasRepository.findReservaById(idReserva)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada tras bloqueo."));

        String estadoActual = freshReserva.getEstado();

        // 3. Validar estados cancelables (solo PENDIENTE o APROBADA)
        if ("CANCELADA".equalsIgnoreCase(estadoActual)) {
            throw new ReservaTransicionInvalidaException("La reserva ya se encuentra cancelada.");
        }
        if ("RECHAZADA".equalsIgnoreCase(estadoActual) || "COMPLETADA".equalsIgnoreCase(estadoActual)) {
            throw new ReservaTransicionInvalidaException(
                "No es posible cancelar una reserva en estado " + estadoActual + "."
            );
        }
        if (!"PENDIENTE".equalsIgnoreCase(estadoActual) && !"APROBADA".equalsIgnoreCase(estadoActual)) {
            throw new ReservaTransicionInvalidaException(
                "Transición no permitida: el estado actual '" + estadoActual + "' no permite cancelación."
            );
        }

        reservasRepository.updateEstadoReserva(idReserva, "CANCELADA", null);
    }

    private void validarTransicionEstado(String estadoActual, String nuevoEstado) {
        if (estadoActual == null) return;
        if (estadoActual.equalsIgnoreCase(nuevoEstado)) {
            return; // Idempotente
        }

        // Estados terminales
        if ("CANCELADA".equalsIgnoreCase(estadoActual)) {
            throw new ReservaTransicionInvalidaException(
                "No se permite modificar una reserva en estado terminal CANCELADA hacia '" + nuevoEstado + "'."
            );
        }
        if ("RECHAZADA".equalsIgnoreCase(estadoActual)) {
            throw new ReservaTransicionInvalidaException(
                "No se permite modificar una reserva en estado terminal RECHAZADA hacia '" + nuevoEstado + "'."
            );
        }
        if ("COMPLETADA".equalsIgnoreCase(estadoActual)) {
            throw new ReservaTransicionInvalidaException(
                "No se permite modificar una reserva en estado terminal COMPLETADA hacia '" + nuevoEstado + "'."
            );
        }

        if ("PENDIENTE".equalsIgnoreCase(estadoActual)) {
            if (!"APROBADA".equalsIgnoreCase(nuevoEstado) 
                    && !"RECHAZADA".equalsIgnoreCase(nuevoEstado) 
                    && !"CANCELADA".equalsIgnoreCase(nuevoEstado)) {
                throw new ReservaTransicionInvalidaException(
                    "Transición no permitida desde PENDIENTE hacia '" + nuevoEstado + "'."
                );
            }
            return;
        }

        if ("APROBADA".equalsIgnoreCase(estadoActual)) {
            if (!"CANCELADA".equalsIgnoreCase(nuevoEstado) && !"COMPLETADA".equalsIgnoreCase(nuevoEstado)) {
                throw new ReservaTransicionInvalidaException(
                    "Transición no permitida desde APROBADA hacia '" + nuevoEstado + "'."
                );
            }
            return;
        }

        throw new ReservaTransicionInvalidaException(
            "Transición no permitida desde '" + estadoActual + "' hacia '" + nuevoEstado + "'."
        );
    }

    @Override
    public PazYSalvoEstadoFinancieroDTO getMiEstadoMora() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUnitId() == null) {
            throw new AccessDeniedException("El usuario no tiene una unidad asignada en su contexto.");
        }
        return pazYSalvoService.verificarEstadoFinanciero(ctx.getUnitId());
    }
}
