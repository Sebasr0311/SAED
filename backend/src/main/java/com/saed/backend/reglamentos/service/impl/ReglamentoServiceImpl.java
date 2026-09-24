package com.saed.backend.reglamentos.service.impl;

import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.service.DocumentoService;
import com.saed.backend.documentos.service.DocumentoService.DocumentoDescarga;
import com.saed.backend.reglamentos.dto.ReglamentoCreateRequestDTO;
import com.saed.backend.reglamentos.dto.ReglamentoDTO;
import com.saed.backend.reglamentos.dto.ReglamentoPublicarRequestDTO;
import com.saed.backend.reglamentos.dto.ReglamentoUpdateRequestDTO;
import com.saed.backend.reglamentos.repository.ReglamentoRepository;
import com.saed.backend.reglamentos.service.ReglamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ReglamentoServiceImpl implements ReglamentoService {

    private static final Logger log = LoggerFactory.getLogger(ReglamentoServiceImpl.class);

    private static final Set<String> VALID_TYPES = Set.of(
        "REGLAMENTO_INTERNO",
        "MANUAL_CONVIVENCIA",
        "MANUAL_ZONAS_COMUNES",
        "MANUAL_POLITICA_MASCOTAS",
        "ESTATUTO_COPROPIEDAD",
        "OTRO"
    );

    private final ReglamentoRepository reglamentoRepository;
    private final DocumentoService documentoService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public ReglamentoServiceImpl(ReglamentoRepository reglamentoRepository,
                                 DocumentoService documentoService,
                                 AuditService auditService,
                                 JdbcTemplate jdbcTemplate) {
        this.reglamentoRepository = reglamentoRepository;
        this.documentoService = documentoService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private SaedContext requireContext() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de autenticación no disponible");
        }
        return ctx;
    }

    private void validateTipoNormativa(String tipo) {
        if (tipo == null || !VALID_TYPES.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de normativa no válido: " + tipo);
        }
    }

    private Long resolvePropertyId(Long explicitPropId) {
        SaedContext ctx = requireContext();
        if (explicitPropId != null) {
            if ("ADMIN_PROPIEDAD".equalsIgnoreCase(ctx.getRoleCode()) && ctx.getPropertyId() != null
                && !ctx.getPropertyId().equals(explicitPropId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para operar en otra propiedad");
            }
            return explicitPropId;
        }
        if (ctx.getPropertyId() != null) {
            return ctx.getPropertyId();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID de propiedad es requerido");
    }

    private Long resolveOrganizationId(Long propId) {
        SaedContext ctx = requireContext();
        if (ctx.getOrganizationId() != null && ctx.getOrganizationId() > 0) {
            return ctx.getOrganizationId();
        }
        try {
            return jdbcTemplate.queryForObject(
                "SELECT ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = ?",
                Long.class,
                propId
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Propiedad no encontrada para resolver organización");
        }
    }

    private void verifyPropertyBelongsToOrg(Long propId, Long orgId) {
        if (propId == null || orgId == null) return;
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM PROPIEDADES WHERE ID_PROPIEDAD = ? AND ID_ORGANIZACION = ?",
            Integer.class,
            propId, orgId
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La propiedad no pertenece a la organización actual");
        }
    }

    private void checkTenantScope(ReglamentoDTO reg) {
        SaedContext ctx = requireContext();
        String role = ctx.getRoleCode();

        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            return;
        }

        if (ctx.getOrganizationId() != null && reg.getIdOrganizacion() != null
            && !ctx.getOrganizationId().equals(reg.getIdOrganizacion())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para acceder a reglamentos de otra organización");
        }

        if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (ctx.getPropertyId() != null && reg.getIdPropiedad() != null
                && !ctx.getPropertyId().equals(reg.getIdPropiedad())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para acceder a reglamentos de otra propiedad");
            }
        }
    }

    @Override
    @Transactional
    public ReglamentoDTO createDraft(ReglamentoCreateRequestDTO request, Long customPropiedadId) {
        SaedContext ctx = requireContext();
        validateTipoNormativa(request.getTipoNormativa());

        Long propId = resolvePropertyId(customPropiedadId != null ? customPropiedadId : request.getIdPropiedad());
        Long orgId = resolveOrganizationId(propId);

        if ("ADMIN_ORGANIZACION".equalsIgnoreCase(ctx.getRoleCode())) {
            verifyPropertyBelongsToOrg(propId, orgId);
        }

        if (!reglamentoRepository.existsDocumentoInPropiedad(request.getIdDocumento(), propId, orgId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El documento no pertenece a la copropiedad o no existe");
        }

        ReglamentoDTO dto = new ReglamentoDTO();
        dto.setIdOrganizacion(orgId);
        dto.setIdPropiedad(propId);
        dto.setTipoNormativa(request.getTipoNormativa());
        dto.setTitulo(request.getTitulo().trim());
        dto.setDescripcion(request.getDescripcion() != null ? request.getDescripcion().trim() : null);
        dto.setIdDocumento(request.getIdDocumento());
        dto.setCreadoPor(ctx.getUserId());

        Long idReglamento = reglamentoRepository.create(dto);

        auditService.recordSuccess(
            ctx.getUserId(), orgId, propId,
            "CREAR_BORRADOR_REGLAMENTO", "REGLAMENTOS_NORMATIVA", idReglamento,
            "127.0.0.1", "SAED-Core", null, "BORRADOR"
        );

        return getById(idReglamento);
    }

    @Override
    @Transactional
    public ReglamentoDTO updateDraft(Long idReglamento, ReglamentoUpdateRequestDTO request) {
        SaedContext ctx = requireContext();
        validateTipoNormativa(request.getTipoNormativa());

        ReglamentoDTO existing = reglamentoRepository.findById(idReglamento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reglamento no encontrado"));

        checkTenantScope(existing);

        if (!"BORRADOR".equals(existing.getEstado())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Solo se pueden modificar reglamentos en estado BORRADOR. Cree un nuevo borrador para actualizar normativa."
            );
        }

        if (!reglamentoRepository.existsDocumentoInPropiedad(request.getIdDocumento(), existing.getIdPropiedad(), existing.getIdOrganizacion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El documento no pertenece a la copropiedad o no existe");
        }

        existing.setTipoNormativa(request.getTipoNormativa());
        existing.setTitulo(request.getTitulo().trim());
        existing.setDescripcion(request.getDescripcion() != null ? request.getDescripcion().trim() : null);
        existing.setIdDocumento(request.getIdDocumento());

        reglamentoRepository.update(existing);

        auditService.recordSuccess(
            ctx.getUserId(), existing.getIdOrganizacion(), existing.getIdPropiedad(),
            "ACTUALIZAR_BORRADOR_REGLAMENTO", "REGLAMENTOS_NORMATIVA", idReglamento,
            "127.0.0.1", "SAED-Core", "BORRADOR", "BORRADOR"
        );

        return getById(idReglamento);
    }

    @Override
    @Transactional
    public ReglamentoDTO publicar(Long idReglamento, ReglamentoPublicarRequestDTO request) {
        SaedContext ctx = requireContext();
        ReglamentoDTO existing = reglamentoRepository.findById(idReglamento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reglamento no encontrado"));

        checkTenantScope(existing);

        if ("PUBLICADO".equals(existing.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reglamento ya se encuentra publicado");
        }

        if (!"BORRADOR".equals(existing.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo reglamentos en estado BORRADOR pueden ser publicados");
        }

        // Pessimistic locking: serialize publication at property level and normative type level
        reglamentoRepository.lockPropiedadForUpdate(existing.getIdPropiedad());
        Optional<Long> currentVigenteId = reglamentoRepository.lockVigenteForUpdate(
            existing.getIdPropiedad(), existing.getTipoNormativa()
        );

        if (currentVigenteId.isPresent() && !currentVigenteId.get().equals(idReglamento)) {
            Long oldId = currentVigenteId.get();
            reglamentoRepository.reemplazar(oldId, ctx.getUserId());
            auditService.recordSuccess(
                ctx.getUserId(), existing.getIdOrganizacion(), existing.getIdPropiedad(),
                "REEMPLAZO_NORMATIVA", "REGLAMENTOS_NORMATIVA", oldId,
                "127.0.0.1", "SAED-Core", "PUBLICADO", "REEMPLAZADO"
            );
        }

        LocalDate fVigor = (request != null && request.getFechaEntradaEnVigor() != null)
            ? request.getFechaEntradaEnVigor()
            : LocalDate.now();

        reglamentoRepository.publicar(idReglamento, fVigor, ctx.getUserId());

        // Ensure underlying document is public for residents to stream/download
        reglamentoRepository.markDocumentoPublicoResidentes(existing.getIdDocumento());

        auditService.recordSuccess(
            ctx.getUserId(), existing.getIdOrganizacion(), existing.getIdPropiedad(),
            "PUBLICACION_NORMATIVA", "REGLAMENTOS_NORMATIVA", idReglamento,
            "127.0.0.1", "SAED-Core", "BORRADOR", "PUBLICADO"
        );

        return getById(idReglamento);
    }

    @Override
    @Transactional
    public ReglamentoDTO inactivar(Long idReglamento) {
        SaedContext ctx = requireContext();
        ReglamentoDTO existing = reglamentoRepository.findById(idReglamento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reglamento no encontrado"));

        checkTenantScope(existing);

        if ("INACTIVO".equals(existing.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reglamento ya se encuentra inactivo");
        }

        String oldEstado = existing.getEstado();
        reglamentoRepository.inactivar(idReglamento, ctx.getUserId());

        auditService.recordSuccess(
            ctx.getUserId(), existing.getIdOrganizacion(), existing.getIdPropiedad(),
            "INACTIVACION_NORMATIVA", "REGLAMENTOS_NORMATIVA", idReglamento,
            "127.0.0.1", "SAED-Core", oldEstado, "INACTIVO"
        );

        return getById(idReglamento);
    }

    @Override
    public ReglamentoDTO getById(Long idReglamento) {
        SaedContext ctx = requireContext();
        ReglamentoDTO reg = reglamentoRepository.findById(idReglamento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reglamento no encontrado"));

        String role = ctx.getRoleCode();
        if ("RESIDENTE".equalsIgnoreCase(role) || "PROPIETARIO_UNIDAD".equalsIgnoreCase(role)
            || "PROPIETARIO".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            if (!"PUBLICADO".equals(reg.getEstado())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado al reglamento");
            }
            if (ctx.getPropertyId() != null && !ctx.getPropertyId().equals(reg.getIdPropiedad())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para ver reglamentos de otra propiedad");
            }
        } else {
            checkTenantScope(reg);
        }

        return reg;
    }

    @Override
    public ReglamentoDTO getVigente(String tipoNormativa, Long idPropiedad) {
        validateTipoNormativa(tipoNormativa);
        Long propId = resolvePropertyId(idPropiedad);

        return reglamentoRepository.findVigenteByPropiedadAndTipo(propId, tipoNormativa)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No existe normativa vigente para el tipo: " + tipoNormativa
            ));
    }

    @Override
    public List<ReglamentoDTO> listAdmin(String tipoNormativa, String estado, Long idPropiedad) {
        SaedContext ctx = requireContext();
        Long propId = (idPropiedad != null) ? resolvePropertyId(idPropiedad) : ctx.getPropertyId();
        Long orgId = ctx.getOrganizationId();

        return reglamentoRepository.findAllAdmin(propId, orgId, tipoNormativa, estado);
    }

    @Override
    public List<ReglamentoDTO> listResidente(String tipoNormativa) {
        SaedContext ctx = requireContext();
        Long propId = ctx.getPropertyId();
        if (propId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario sin asignación de propiedad activa");
        }

        return reglamentoRepository.findAllResidente(propId, tipoNormativa);
    }

    @Override
    public DocumentoDescarga downloadDocumento(Long idReglamento) {
        ReglamentoDTO reg = getById(idReglamento);
        return documentoService.downloadDocumento(reg.getIdDocumento());
    }
}
