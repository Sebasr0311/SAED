package com.saed.backend.domicilios.service.impl;

import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.domicilios.dto.DomicilioCreateDTO;
import com.saed.backend.domicilios.dto.DomicilioDTO;
import com.saed.backend.domicilios.repository.DomicilioRepository;
import com.saed.backend.domicilios.service.DomicilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saed.backend.authorization.service.PropertyConfigService;
import com.saed.backend.authorization.service.PropertyConfigServiceImpl;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class DomicilioServiceImpl implements DomicilioService {

    private static final Logger log = LoggerFactory.getLogger(DomicilioServiceImpl.class);

    private final DomicilioRepository domicilioRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final PropertyConfigService propertyConfigService;

    public DomicilioServiceImpl(DomicilioRepository domicilioRepository,
                                NamedParameterJdbcTemplate jdbcTemplate,
                                AuditService auditService,
                                PropertyConfigService propertyConfigService) {
        this.domicilioRepository = domicilioRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.propertyConfigService = propertyConfigService;
    }

    private Long resolveActivePropertyId(SaedContext ctx) {
        if (ctx != null && ctx.getPropertyId() != null) {
            return ctx.getPropertyId();
        }
        return 1L;
    }

    private Long resolveActiveUserId(SaedContext ctx) {
        if (ctx != null && ctx.getUserId() != null) {
            return ctx.getUserId();
        }
        return 1L;
    }

    private boolean isSuperAdmin(SaedContext ctx) {
        return ctx != null && "SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode());
    }

    private boolean isResidente(SaedContext ctx) {
        if (ctx == null) return false;
        String role = ctx.getRoleCode();
        return "RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role);
    }

    @Override
    @Transactional
    public DomicilioDTO registrarDomicilio(DomicilioCreateDTO dto) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long activePropId = resolveActivePropertyId(ctx);
        Long activeUserId = resolveActiveUserId(ctx);

        // 1. Validar existencia y tenencia de la unidad (Anti-IDOR)
        List<Map<String, Object>> unitRows = jdbcTemplate.queryForList(
                "SELECT u.ID_PROPIEDAD, p.ID_ORGANIZACION FROM UNIDADES u JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE u.ID_UNIDAD = :unitId",
                new MapSqlParameterSource("unitId", dto.idUnidad())
        );

        if (unitRows.isEmpty()) {
            boolean existsGlobal = false;
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                Integer c = jdbcTemplate.getJdbcOperations().queryForObject(
                    "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = ?",
                    Integer.class, dto.idUnidad()
                );
                existsGlobal = (c != null && c > 0);
            } catch (Exception ignored) {
            } finally {
                if (activeUserId != null) {
                    try {
                        jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(" + activeUserId + "); END;");
                    } catch (Exception ignored) {}
                }
            }
            if (existsGlobal) {
                throw new AccessDeniedException("La unidad especificada no pertenece a la copropiedad activa");
            }
            throw new NoSuchElementException("La unidad especificada no existe: " + dto.idUnidad());
        }

        Map<String, Object> unitRow = unitRows.get(0);
        Long unitPropId = ((Number) unitRow.get("ID_PROPIEDAD")).longValue();
        Long unitOrgId = ((Number) unitRow.get("ID_ORGANIZACION")).longValue();

        if (!isSuperAdmin(ctx) && !unitPropId.equals(activePropId)) {
            throw new AccessDeniedException("La unidad especificada no pertenece a la copropiedad activa");
        }

        // Si es residente, solo puede registrar o recibir en su propia unidad
        if (isResidente(ctx) && ctx.getUnitId() != null && !ctx.getUnitId().equals(dto.idUnidad())) {
            throw new AccessDeniedException("No tiene permisos para registrar domicilios para otra unidad");
        }

        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : unitOrgId;

        // 2. Persistir en tabla canónica DOMICILIOS
        DomicilioDTO saved = domicilioRepository.registrarDomicilio(dto, orgId, unitPropId, activeUserId);

        // 3. Registrar acceso en bitácora de portería (REGISTROS_ACCESO)
        try {
            Long porteriaId = dto.idPorteria();
            if (porteriaId == null) {
                List<Long> pList = jdbcTemplate.queryForList(
                        "SELECT MIN(ID_PORTERIA) FROM PORTERIAS WHERE ID_PROPIEDAD = :propId",
                        new MapSqlParameterSource("propId", unitPropId),
                        Long.class
                );
                porteriaId = (pList != null && !pList.isEmpty() && pList.get(0) != null) ? pList.get(0) : 1L;
            }

            Long personaId = dto.idPersonaDestinatario();
            if (personaId == null) {
                List<Long> pList = jdbcTemplate.queryForList(
                        "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                        new MapSqlParameterSource("u", activeUserId),
                        Long.class
                );
                personaId = (pList != null && !pList.isEmpty() && pList.get(0) != null) ? pList.get(0) : 1L;
            }

            jdbcTemplate.update("""
                INSERT INTO REGISTROS_ACCESO (
                    ID_PROPIEDAD, ID_PORTERIA, ID_PERSONA, ID_UNIDAD,
                    TIPO_MOVIMIENTO, METODO_AUTORIZACION, PORTERO_OPERADOR, OBSERVACIONES
                ) VALUES (
                    :prop, :port, :persona, :unidad, 'ENTRADA', 'MANUAL_PORTERO', :operador, :obs
                )
            """, new MapSqlParameterSource()
                    .addValue("prop", unitPropId)
                    .addValue("port", porteriaId)
                    .addValue("persona", personaId)
                    .addValue("unidad", dto.idUnidad())
                    .addValue("operador", activeUserId)
                    .addValue("obs", "Domicilio: " + dto.empresa() + " - " + dto.nombreDomiciliario())
            );
        } catch (Exception e) {
            log.warn("Aviso al registrar evento en REGISTROS_ACCESO: {}", e.getMessage());
        }

        return enriquecerPermanencia(saved, unitPropId);
    }

    private DomicilioDTO enriquecerPermanencia(DomicilioDTO dto, Long activePropId) {
        if (dto == null) return null;
        Long propId = dto.idPropiedad() != null ? dto.idPropiedad() : activePropId;
        int maxMinutos = 30;
        if (propId != null && propertyConfigService != null) {
            maxMinutos = propertyConfigService.getIntValue(propId, PropertyConfigServiceImpl.KEY_TIEMPO_MAXIMO_DOMICILIO, 30);
        }
        Long minutos = null;
        Boolean excedido = false;
        if (dto.fechaEntrada() != null) {
            if ("EN_CURSO".equalsIgnoreCase(dto.estado())) {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
                minutos = Math.max(0, Duration.between(dto.fechaEntrada(), now).toMinutes());
                excedido = minutos > maxMinutos;
            } else if (dto.fechaSalida() != null) {
                minutos = Math.max(0, Duration.between(dto.fechaEntrada(), dto.fechaSalida()).toMinutes());
                excedido = minutos > maxMinutos;
            }
        }
        return dto.withPermanencia(minutos, maxMinutos, excedido);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomicilioDTO> listarDomicilios(String estado) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long activePropId = isSuperAdmin(ctx) ? null : resolveActivePropertyId(ctx);
        Long activeUnitId = (isResidente(ctx)) ? ctx.getUnitId() : null;

        List<DomicilioDTO> list = domicilioRepository.listarDomicilios(activePropId, estado, activeUnitId);
        return list.stream().map(d -> enriquecerPermanencia(d, activePropId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DomicilioDTO buscarPorId(Long idDomicilio) {
        SaedContext ctx = SaedContextHolder.getContext();
        DomicilioDTO dom = domicilioRepository.buscarPorId(idDomicilio)
                .orElseThrow(() -> new NoSuchElementException("Domicilio no encontrado con ID: " + idDomicilio));

        if (!isSuperAdmin(ctx)) {
            Long activePropId = resolveActivePropertyId(ctx);
            if (!dom.idPropiedad().equals(activePropId)) {
                throw new AccessDeniedException("No tiene permisos para acceder a domicilios de otra propiedad");
            }
            if (isResidente(ctx) && ctx.getUnitId() != null && !dom.idUnidad().equals(ctx.getUnitId())) {
                throw new AccessDeniedException("No tiene permisos para consultar domicilios de otra unidad");
            }
        }

        return enriquecerPermanencia(dom, dom.idPropiedad());
    }

    @Override
    @Transactional
    public DomicilioDTO finalizarDomicilio(Long idDomicilio) {
        SaedContext ctx = SaedContextHolder.getContext();
        DomicilioDTO existente = buscarPorId(idDomicilio);
        Long activeUserId = resolveActiveUserId(ctx);

        DomicilioDTO finalizado = domicilioRepository.finalizarDomicilio(idDomicilio, activeUserId);

        // Bitácora de salida
        try {
            Long personaId = null;
            try {
                personaId = jdbcTemplate.queryForObject(
                        "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                        new MapSqlParameterSource("u", activeUserId),
                        Long.class
                );
            } catch (Exception ignored) {}
            if (personaId == null) personaId = 1L;

            jdbcTemplate.update("""
                INSERT INTO REGISTROS_ACCESO (
                    ID_PROPIEDAD, ID_PORTERIA, ID_PERSONA, ID_UNIDAD,
                    TIPO_MOVIMIENTO, METODO_AUTORIZACION, PORTERO_OPERADOR, OBSERVACIONES
                ) VALUES (
                    :prop, :port, :persona, :unidad, 'SALIDA', 'MANUAL_PORTERO', :operador, :obs
                )
            """, new MapSqlParameterSource()
                    .addValue("prop", finalizado.idPropiedad())
                    .addValue("port", finalizado.idPorteria())
                    .addValue("persona", personaId)
                    .addValue("unidad", finalizado.idUnidad())
                    .addValue("operador", activeUserId)
                    .addValue("obs", "Salida Domicilio: " + finalizado.empresa() + " - " + finalizado.nombreDomiciliario())
            );
        } catch (Exception e) {
            log.debug("Aviso al registrar salida en REGISTROS_ACCESO: {}", e.getMessage());
        }

        return enriquecerPermanencia(finalizado, finalizado.idPropiedad());
    }

    @Override
    @Transactional
    public DomicilioDTO cancelarDomicilio(Long idDomicilio) {
        SaedContext ctx = SaedContextHolder.getContext();
        DomicilioDTO existente = buscarPorId(idDomicilio);
        Long activeUserId = resolveActiveUserId(ctx);

        DomicilioDTO cancelado = domicilioRepository.cancelarDomicilio(idDomicilio, activeUserId);

        // Bitácora de cancelación
        try {
            Long personaId = null;
            try {
                personaId = jdbcTemplate.queryForObject(
                        "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                        new MapSqlParameterSource("u", activeUserId),
                        Long.class
                );
            } catch (Exception ignored) {}
            if (personaId == null) personaId = 1L;

            jdbcTemplate.update("""
                INSERT INTO REGISTROS_ACCESO (
                    ID_PROPIEDAD, ID_PORTERIA, ID_PERSONA, ID_UNIDAD,
                    TIPO_MOVIMIENTO, METODO_AUTORIZACION, PORTERO_OPERADOR, OBSERVACIONES
                ) VALUES (
                    :prop, :port, :persona, :unidad, 'SALIDA', 'MANUAL_PORTERO', :operador, :obs
                )
            """, new MapSqlParameterSource()
                    .addValue("prop", cancelado.idPropiedad())
                    .addValue("port", cancelado.idPorteria())
                    .addValue("persona", personaId)
                    .addValue("unidad", cancelado.idUnidad())
                    .addValue("operador", activeUserId)
                    .addValue("obs", "Cancelación Domicilio: " + cancelado.empresa() + " - " + cancelado.nombreDomiciliario())
            );
        } catch (Exception e) {
            log.debug("Aviso al registrar cancelación en REGISTROS_ACCESO: {}", e.getMessage());
        }

        return enriquecerPermanencia(cancelado, cancelado.idPropiedad());
    }
}
