package com.saed.backend.trabajadores.service.impl;

import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.repository.ObraRepository;
import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.repository.PersonaRepository;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.exception.ProveedorNoEncontradoException;
import com.saed.backend.proveedores.repository.ProveedorRepository;
import com.saed.backend.trabajadores.dto.ObraTrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorCreateDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorUpdateDTO;
import com.saed.backend.trabajadores.exception.ArlVencidaException;
import com.saed.backend.trabajadores.exception.DocumentoTrabajadorDuplicadoException;
import com.saed.backend.trabajadores.exception.ProveedorInactivoException;
import com.saed.backend.trabajadores.exception.TrabajadorInactivoException;
import com.saed.backend.trabajadores.exception.TrabajadorNoAutorizadoException;
import com.saed.backend.trabajadores.exception.TrabajadorNoEncontradoException;
import com.saed.backend.trabajadores.exception.TrabajadorObraDuplicadoException;
import com.saed.backend.trabajadores.exception.TrabajadorSinArlException;
import com.saed.backend.trabajadores.repository.ObraTrabajadorRepository;
import com.saed.backend.trabajadores.repository.TrabajadorRepository;
import com.saed.backend.trabajadores.service.TrabajadorService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class TrabajadorServiceImpl implements TrabajadorService {

    private final TrabajadorRepository trabajadorRepository;
    private final ObraTrabajadorRepository obraTrabajadorRepository;
    private final ProveedorRepository proveedorRepository;
    private final PersonaRepository personaRepository;
    private final ObraRepository obraRepository;
    private final PropertyRepository propertyRepository;

    public TrabajadorServiceImpl(TrabajadorRepository trabajadorRepository,
                                 ObraTrabajadorRepository obraTrabajadorRepository,
                                 ProveedorRepository proveedorRepository,
                                 PersonaRepository personaRepository,
                                 ObraRepository obraRepository,
                                 PropertyRepository propertyRepository) {
        this.trabajadorRepository = trabajadorRepository;
        this.obraTrabajadorRepository = obraTrabajadorRepository;
        this.proveedorRepository = proveedorRepository;
        this.personaRepository = personaRepository;
        this.obraRepository = obraRepository;
        this.propertyRepository = propertyRepository;
    }

    private Long resolveOrganizationId() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            throw new AccessDeniedException("No se encontró contexto de seguridad en la sesión.");
        }

        if (ctx.getOrganizationId() != null) {
            return ctx.getOrganizationId();
        }

        if (ctx.getPropertyId() != null) {
            return propertyRepository.findById(ctx.getPropertyId())
                    .map(p -> p.getIdOrganizacion())
                    .orElseThrow(() -> new AccessDeniedException("No se pudo resolver la organización asociada a la propiedad activa " + ctx.getPropertyId()));
        }

        throw new AccessDeniedException("No se pudo determinar la organización activa para la operación.");
    }

    private static String currentRole() {
        SaedContext ctx = SaedContextHolder.getContext();
        return ctx != null ? ctx.getRoleCode() : null;
    }

    /** Property/organization admins manage the worker registry; SUPERADMIN has no operational access (DEC-02). */
    private static boolean isAdmin() {
        String role = currentRole();
        return "ADMIN_ORGANIZACION".equals(role) || "ADMIN_PROPIEDAD".equals(role);
    }

    private static boolean isUnitMember() {
        String role = currentRole();
        return "RESIDENTE".equals(role) || "PROPIETARIO".equals(role);
    }

    private static void requireWorkerReader() {
        if (!isAdmin() && !isUnitMember()) {
            throw new AccessDeniedException("Operación denegada para el rol actual.");
        }
    }

    private static Long requirePropertyId() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            throw new AccessDeniedException("No se encontró una propiedad activa en la sesión.");
        }
        return ctx.getPropertyId();
    }

    private Long getUserIdFromContext() {
        SaedContext ctx = SaedContextHolder.getContext();
        return (ctx != null) ? ctx.getUserId() : null;
    }

    private ObraDTO validarAccesoObra(Long idObra) {
        ObraDTO obra = obraRepository.findByIdDirecto(idObra)
                .orElseThrow(() -> new AccessDeniedException("Acceso denegado: obra no encontrada o no autorizada para el usuario"));

        SaedContext ctx = SaedContextHolder.getContext();
        requireWorkerReader();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if (!isAdmin()) {
                Long userUnitId = ctx.getUnitId();
                if (userUnitId == null || !userUnitId.equals(obra.getIdUnidad())) {
                    throw new AccessDeniedException("Acceso denegado: no tiene permisos para gestionar trabajadores en esta obra");
                }
            } else if ("ADMIN_PROPIEDAD".equals(role) && ctx.getPropertyId() != null) {
                obraRepository.findById(idObra, ctx.getPropertyId())
                        .orElseThrow(() -> new AccessDeniedException("Acceso denegado: la obra no pertenece a la propiedad activa"));
            }
        }
        return obra;
    }

    private void validarAccesoAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Operación denegada: solo la administración puede autorizar o revocar trabajadores.");
        }
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "TRABAJADOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public TrabajadorDTO crearTrabajador(TrabajadorCreateDTO dto) {
        Long idOrganizacion = resolveOrganizationId();

        if (dto.idProveedor() == null) {
            throw new IllegalArgumentException("El proveedor es obligatorio");
        }

        ProveedorDTO proveedor = proveedorRepository.buscarPorId(dto.idProveedor(), idOrganizacion)
                .orElseThrow(() -> new ProveedorNoEncontradoException(dto.idProveedor()));

        if (!"ACTIVO".equalsIgnoreCase(proveedor.estado())) {
            throw new ProveedorInactivoException("El proveedor " + proveedor.razonSocial() + " no está activo");
        }

        if (dto.arlAseguradora() == null || dto.arlAseguradora().isBlank()) {
            throw new IllegalArgumentException("La aseguradora ARL es obligatoria");
        }

        if (dto.arlFechaVencimiento() == null) {
            throw new IllegalArgumentException("La fecha de vencimiento de la ARL es obligatoria");
        }

        LocalDate hoy = LocalDate.now();
        if (dto.arlFechaVencimiento().isBefore(hoy)) {
            throw new ArlVencidaException("No se puede registrar un trabajador con ARL vencida (" + dto.arlFechaVencimiento() + ")");
        }

        // Resolving Persona
        Long idPersona = dto.idPersona();
        if (idPersona == null) {
            if (dto.numeroDocumento() == null || dto.numeroDocumento().isBlank()) {
                throw new IllegalArgumentException("Se requiere idPersona o numeroDocumento para registrar al trabajador");
            }

            Optional<PersonaDTO> existingPersona = personaRepository.findByNumeroDocumento(dto.numeroDocumento().trim());
            if (existingPersona.isPresent()) {
                idPersona = existingPersona.get().id();
            } else {
                Long tipoDocId = dto.tipoDocumentoId();
                if (tipoDocId == null) {
                    tipoDocId = personaRepository.findTipoDocumentoIdByCodigo("CC").orElse(1L);
                }

                PersonaRequestDTO personaReq = new PersonaRequestDTO(
                        tipoDocId,
                        dto.numeroDocumento().trim(),
                        "NATURAL",
                        dto.primerNombre() != null ? dto.primerNombre().trim() : "",
                        dto.segundoNombre(),
                        dto.primerApellido() != null ? dto.primerApellido().trim() : "",
                        dto.segundoApellido(),
                        dto.email(),
                        dto.telefono()
                );
                idPersona = personaRepository.insert(personaReq);
            }
        }

        // Uniqueness check per provider
        if (trabajadorRepository.existePorProveedorYPersona(dto.idProveedor(), idPersona, null)) {
            throw new DocumentoTrabajadorDuplicadoException("El trabajador ya se encuentra registrado para este proveedor");
        }

        return trabajadorRepository.crear(idPersona, dto);
    }

    @Override
    public List<TrabajadorDTO> listarTrabajadores(Long idProveedor, String estado, String search) {
        requireWorkerReader();
        if (!isAdmin()) {
            // F9-02: residents/owners only see minimal data of workers relevant to their own property.
            return trabajadorRepository.listarParaPropiedad(requirePropertyId(), search).stream()
                    .map(TrabajadorDTO::resumenPublico)
                    .toList();
        }
        Long idOrganizacion = resolveOrganizationId();
        return trabajadorRepository.listar(idOrganizacion, idProveedor, estado, search);
    }

    @Override
    public TrabajadorDTO obtenerTrabajador(Long idTrabajador) {
        requireWorkerReader();
        if (!isAdmin()) {
            return trabajadorRepository.buscarPorIdEnPropiedad(idTrabajador, requirePropertyId())
                    .map(TrabajadorDTO::resumenPublico)
                    .orElseThrow(() -> new TrabajadorNoEncontradoException(idTrabajador));
        }
        Long idOrganizacion = resolveOrganizationId();
        return trabajadorRepository.buscarPorId(idTrabajador, idOrganizacion)
                .orElseThrow(() -> new TrabajadorNoEncontradoException(idTrabajador));
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", resource = "TRABAJADOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public TrabajadorDTO actualizarTrabajador(Long idTrabajador, TrabajadorUpdateDTO dto) {
        Long idOrganizacion = resolveOrganizationId();

        TrabajadorDTO actual = trabajadorRepository.buscarPorId(idTrabajador, idOrganizacion)
                .orElseThrow(() -> new TrabajadorNoEncontradoException(idTrabajador));

        if (dto.arlFechaVencimiento() != null && dto.arlFechaVencimiento().isBefore(LocalDate.now())) {
            throw new ArlVencidaException("No se puede actualizar con ARL vencida (" + dto.arlFechaVencimiento() + ")");
        }

        trabajadorRepository.actualizar(idTrabajador, idOrganizacion, dto);
        return trabajadorRepository.buscarPorId(idTrabajador, idOrganizacion)
                .orElseThrow(() -> new TrabajadorNoEncontradoException(idTrabajador));
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE_STATUS", resource = "TRABAJADOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void cambiarEstado(Long idTrabajador, String nuevoEstado) {
        if (nuevoEstado == null || (!nuevoEstado.equalsIgnoreCase("ACTIVO") && !nuevoEstado.equalsIgnoreCase("INACTIVO"))) {
            throw new IllegalArgumentException("El estado debe ser ACTIVO o INACTIVO");
        }
        Long idOrganizacion = resolveOrganizationId();
        trabajadorRepository.buscarPorId(idTrabajador, idOrganizacion)
                .orElseThrow(() -> new TrabajadorNoEncontradoException(idTrabajador));

        trabajadorRepository.actualizarEstado(idTrabajador, idOrganizacion, nuevoEstado.trim().toUpperCase());
    }

    @Override
    public List<ObraTrabajadorDTO> listarTrabajadoresObra(Long idObra) {
        validarAccesoObra(idObra);
        List<ObraTrabajadorDTO> asignados = obraTrabajadorRepository.listarPorObra(idObra);
        if (isAdmin()) {
            return asignados;
        }
        return asignados.stream()
                .map(ot -> new ObraTrabajadorDTO(ot.idObraTrabajador(), ot.idObra(), ot.idTrabajador(), ot.autorizado(),
                        ot.fechaAutorizacion(), ot.fechaRevocacion(), ot.autorizadoPor(),
                        ot.trabajador() != null ? ot.trabajador().resumenPublico() : null))
                .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "ASSIGN_TO_OBRA", resource = "TRABAJADOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.HIGH)
    public void asignarTrabajadorObra(Long idObra, Long idTrabajador, String autorizado) {
        validarAccesoObra(idObra);
        Long idOrganizacion = resolveOrganizationId();

        TrabajadorDTO trabajador = trabajadorRepository.buscarPorId(idTrabajador, idOrganizacion)
                .orElseThrow(() -> new TrabajadorNoEncontradoException(idTrabajador));

        // Check if already assigned
        if (obraTrabajadorRepository.buscarPorObraYTrabajador(idObra, idTrabajador).isPresent()) {
            throw new TrabajadorObraDuplicadoException("El trabajador ya se encuentra asignado a esta obra");
        }

        SaedContext ctx = SaedContextHolder.getContext();

        String aut;
        Long autorizadoPor;
        if (isAdmin()) {
            aut = (autorizado != null && autorizado.equalsIgnoreCase("N")) ? "N" : "S";
            if ("S".equals(aut)) {
                validarRequisitosTrabajador(trabajador);
                autorizadoPor = (ctx != null) ? ctx.getUserId() : null;
            } else {
                autorizadoPor = null;
            }
        } else {
            // Regla mandatoria: El residente asigna operarios en estado no autorizado 'N'.
            // Blindaje contra request tampering: se ignora cualquier valor 'S' enviado por el cliente.
            aut = "N";
            autorizadoPor = null;
        }

        obraTrabajadorRepository.asignarOActualizar(idObra, idTrabajador, aut, autorizadoPor);
    }

    @Override
    @Transactional
    @Auditable(action = "AUTHORIZE_IN_OBRA", resource = "TRABAJADOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.HIGH)
    public void autorizarTrabajadorObra(Long idObra, Long idTrabajador) {
        validarAccesoAdmin();
        validarAccesoObra(idObra);
        Long idOrganizacion = resolveOrganizationId();

        TrabajadorDTO trabajador = trabajadorRepository.buscarPorId(idTrabajador, idOrganizacion)
                .orElseThrow(() -> new TrabajadorNoEncontradoException(idTrabajador));

        validarRequisitosTrabajador(trabajador);

        Long userId = getUserIdFromContext();
        obraTrabajadorRepository.autorizar(idObra, idTrabajador, userId);
    }

    @Override
    @Transactional
    @Auditable(action = "REVOKE_FROM_OBRA", resource = "TRABAJADOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.HIGH)
    public void revocarTrabajadorObra(Long idObra, Long idTrabajador) {
        validarAccesoAdmin();
        validarAccesoObra(idObra);
        obraTrabajadorRepository.revocar(idObra, idTrabajador);
    }

    @Override
    @Transactional
    @Auditable(action = "UNASSIGN_FROM_OBRA", resource = "TRABAJADOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.HIGH)
    public void desasignarTrabajadorObra(Long idObra, Long idTrabajador) {
        validarAccesoObra(idObra);
        obraTrabajadorRepository.desasignar(idObra, idTrabajador);
    }

    @Override
    public void validarTrabajadoresParaEjecucionObra(Long idObra) {
        long totalAsignados = obraTrabajadorRepository.contarTotalAsignados(idObra);
        if (totalAsignados == 0) {
            // 0 trabajadores representa autogestión/ejecución directa del residente (GAP-F9-04 test 07)
            return;
        }

        long totalAutorizados = obraTrabajadorRepository.contarAutorizados(idObra);
        if (totalAutorizados == 0) {
            throw new TrabajadorNoAutorizadoException("La obra tiene trabajadores asignados pero ninguno cuenta con autorización activa para ejecución");
        }

        List<ObraTrabajadorDTO> autorizados = obraTrabajadorRepository.listarAutorizadosPorObra(idObra);
        for (ObraTrabajadorDTO ot : autorizados) {
            TrabajadorDTO t = ot.trabajador();
            if (t == null) {
                throw new TrabajadorNoEncontradoException(ot.idTrabajador());
            }
            validarRequisitosTrabajador(t);
        }
    }

    private void validarRequisitosTrabajador(TrabajadorDTO trabajador) {
        if (!"ACTIVO".equalsIgnoreCase(trabajador.estado())) {
            throw new TrabajadorInactivoException("El trabajador " + trabajador.nombreCompleto() + " está inactivo");
        }

        if (trabajador.idProveedor() != null) {
            ProveedorDTO proveedor = proveedorRepository.buscarPorIdDirecto(trabajador.idProveedor())
                    .orElse(null);
            if (proveedor != null && !"ACTIVO".equalsIgnoreCase(proveedor.estado())) {
                throw new ProveedorInactivoException("El proveedor " + proveedor.razonSocial() + " del trabajador está inactivo");
            }
        }

        if (trabajador.arlAseguradora() == null || trabajador.arlAseguradora().isBlank()
                || trabajador.arlFechaVencimiento() == null) {
            throw new TrabajadorSinArlException("El trabajador " + trabajador.nombreCompleto() + " no cuenta con afiliación ARL registrada");
        }

        LocalDate hoy = LocalDate.now();

        // Rango inválido: afiliación posterior a vencimiento
        if (trabajador.arlFechaAfiliacion() != null && trabajador.arlFechaAfiliacion().isAfter(trabajador.arlFechaVencimiento())) {
            throw new ArlVencidaException("Inconsistencia en fechas ARL: la fecha de afiliación (" 
                    + trabajador.arlFechaAfiliacion() + ") no puede ser posterior a la fecha de vencimiento (" 
                    + trabajador.arlFechaVencimiento() + ")");
        }

        // Vencimiento en el pasado
        if (trabajador.arlFechaVencimiento().isBefore(hoy)) {
            throw new ArlVencidaException("La ARL del trabajador " + trabajador.nombreCompleto() + " se encuentra vencida desde " + trabajador.arlFechaVencimiento());
        }

        // Afiliación futura: cobertura aún no iniciada
        if (trabajador.arlFechaAfiliacion() != null && trabajador.arlFechaAfiliacion().isAfter(hoy)) {
            throw new ArlVencidaException("La cobertura ARL del trabajador " + trabajador.nombreCompleto() + " aún no ha iniciado (afiliación: " + trabajador.arlFechaAfiliacion() + ")");
        }
    }
}
