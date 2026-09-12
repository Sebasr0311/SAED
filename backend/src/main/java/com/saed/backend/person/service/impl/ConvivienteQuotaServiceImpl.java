package com.saed.backend.person.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.ConvivienteQuotaDTO;
import com.saed.backend.person.exception.ConvivienteLimitExceededException;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ConvivienteQuotaServiceImpl implements ConvivienteQuotaService {

    private static final Logger log = LoggerFactory.getLogger(ConvivienteQuotaServiceImpl.class);
    public static final String CONFIG_KEY_LIMITE = "LIMITE_CONVIVIENTES_POR_UNIDAD";
    public static final int DEFAULT_LIMIT = 4;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ConvivienteQuotaServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public ConvivienteQuotaDTO getQuota(Long unitId) {
        if (unitId == null) {
            throw new IllegalArgumentException("El identificador de la unidad es obligatorio");
        }

        validateUnitScope(unitId);
        Long propertyId = findUnitPropertyId(unitId);
        validatePropertyScope(propertyId);

        int limit = getLimitForProperty(propertyId);
        int activeConvivientes = countActiveConvivientes(unitId);
        int cuposDisponibles = Math.max(0, limit - activeConvivientes);
        boolean limiteAlcanzado = activeConvivientes >= limit;

        return new ConvivienteQuotaDTO(
                unitId,
                propertyId,
                limit,
                activeConvivientes,
                cuposDisponibles,
                limiteAlcanzado
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void validateAndLockQuota(Long unitId) {
        if (unitId == null) {
            throw new IllegalArgumentException("El identificador de la unidad es obligatorio");
        }

        // 1. Bloqueo pesimista a nivel de fila de la unidad (evita race conditions entre transacciones concurrentes)
        validateUnitScope(unitId);
        Long propertyId = lockUnitForUpdate(unitId);
        validatePropertyScope(propertyId);

        // 2. Obtener límite configurado
        int limit = getLimitForProperty(propertyId);

        // 3. Contar convivientes actualmente activos (titular/principal excluido)
        int activeCount = countActiveConvivientes(unitId);

        // 4. Validar disponibilidad
        if (activeCount >= limit) {
            log.warn("Rechazado intento de alta de conviviente: unidad {} superó límite (límite={}, activos={})",
                    unitId, limit, activeCount);
            throw new ConvivienteLimitExceededException(unitId, limit, activeCount);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void validateAndLockQuotaForReactivation(Long unitId, Long residentId) {
        if (unitId == null || residentId == null) {
            throw new IllegalArgumentException("unitId y residentId son obligatorios");
        }

        validateUnitScope(unitId);
        Long propertyId = lockUnitForUpdate(unitId);
        validatePropertyScope(propertyId);

        // Verificar el tipo y estado actual del habitante
        String sqlResident = """
            SELECT TIPO_RESIDENTE, ESTADO
            FROM RESIDENTES_UNIDAD
            WHERE ID_UNIDAD = :unitId AND ID_RESIDENTE_UNIDAD = :residentId
            """;
        List<java.util.Map<String, Object>> resList = jdbcTemplate.queryForList(
                sqlResident,
                new MapSqlParameterSource("unitId", unitId).addValue("residentId", residentId)
        );

        if (resList.isEmpty()) {
            throw new NoSuchElementException("Habitante con ID " + residentId + " no encontrado en la unidad " + unitId);
        }

        String tipoResidente = (String) resList.get(0).get("TIPO_RESIDENTE");
        String estado = (String) resList.get(0).get("ESTADO");

        // Si es titular (PROPIETARIO o ARRENDATARIO), no consume cupo de convivientes
        if (!isConvivienteType(tipoResidente)) {
            return;
        }

        // Si ya está activo, no incrementará el conteo
        if ("ACTIVO".equalsIgnoreCase(estado)) {
            return;
        }

        int limit = getLimitForProperty(propertyId);
        int activeCount = countActiveConvivientes(unitId);

        if (activeCount >= limit) {
            log.warn("Rechazada reactivación de conviviente {}: unidad {} alcanzó el límite (límite={}, activos={})",
                    residentId, unitId, limit, activeCount);
            throw new ConvivienteLimitExceededException(unitId, limit, activeCount);
        }
    }

    @Override
    public int getLimitForProperty(Long propertyId) {
        if (propertyId == null) {
            return DEFAULT_LIMIT;
        }

        String sql = """
            SELECT VALOR
            FROM PROPIEDAD_CONFIGURACION
            WHERE ID_PROPIEDAD = :propId AND CLAVE = :clave
            """;

        try {
            List<String> values = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("propId", propertyId).addValue("clave", CONFIG_KEY_LIMITE),
                    (rs, rowNum) -> rs.getString("VALOR")
            );

            if (!values.isEmpty() && values.get(0) != null) {
                String valStr = values.get(0).trim();
                try {
                    int parsed = Integer.parseInt(valStr);
                    if (parsed > 0) {
                        return parsed;
                    } else {
                        log.warn("Valor no positivo ({}) para {} en propiedad {}. Usando fallback {}",
                                valStr, CONFIG_KEY_LIMITE, propertyId, DEFAULT_LIMIT);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Valor no numérico ('{}') para {} en propiedad {}. Usando fallback {}",
                            valStr, CONFIG_KEY_LIMITE, propertyId, DEFAULT_LIMIT);
                }
            }
        } catch (Exception e) {
            log.warn("Error al consultar {} para propiedad {}: {}. Usando fallback {}",
                    CONFIG_KEY_LIMITE, propertyId, e.getMessage(), DEFAULT_LIMIT);
        }

        return DEFAULT_LIMIT;
    }

    private Long lockUnitForUpdate(Long unitId) {
        String lockSql = "SELECT ID_PROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = :unitId FOR UPDATE";
        try {
            return jdbcTemplate.queryForObject(
                    lockSql,
                    new MapSqlParameterSource("unitId", unitId),
                    Long.class
            );
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException("Unidad no encontrada con ID: " + unitId);
        }
    }

    private Long findUnitPropertyId(Long unitId) {
        String sql = "SELECT ID_PROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = :unitId";
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource("unitId", unitId),
                    Long.class
            );
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException("Unidad no encontrada con ID: " + unitId);
        }
    }

    private int countActiveConvivientes(Long unitId) {
        // Excluye explícitamente titular/principal ('PROPIETARIO', 'ARRENDATARIO', 'TITULAR')
        // Solo computan convivientes activos: ('CONVIVIENTE', 'FAMILIAR', 'OTRO')
        String sql = """
            SELECT COUNT(1)
            FROM RESIDENTES_UNIDAD
            WHERE ID_UNIDAD = :unitId
              AND ESTADO = 'ACTIVO'
              AND (FECHA_FIN IS NULL OR FECHA_FIN > TRUNC(SYSDATE))
              AND TIPO_RESIDENTE IN ('CONVIVIENTE', 'FAMILIAR', 'OTRO')
            """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("unitId", unitId),
                Integer.class
        );
        return count != null ? count : 0;
    }

    private boolean isConvivienteType(String tipoResidente) {
        if (tipoResidente == null) return false;
        String t = tipoResidente.trim().toUpperCase();
        return "CONVIVIENTE".equals(t) || "FAMILIAR".equals(t) || "OTRO".equals(t);
    }

    private void validateUnitScope(Long unitId) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            return;
        }

        String roleCode = ctx.getRoleCode();
        if ("SUPERADMIN".equals(roleCode)) {
            return;
        }

        if ("RESIDENTE".equals(roleCode) || "UNIDAD".equals(ctx.getRoleScope())) {
            Long callerUnitId = ctx.getUnitId();
            if (callerUnitId == null && ctx.getUserId() != null) {
                List<Long> uList = jdbcTemplate.queryForList(
                        "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA WHERE u.ID_USUARIO = :uid AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1",
                        new MapSqlParameterSource("uid", ctx.getUserId()),
                        Long.class
                );
                if (!uList.isEmpty()) {
                    callerUnitId = uList.get(0);
                }
            }

            if (callerUnitId == null || !callerUnitId.equals(unitId)) {
                throw new AccessDeniedException("Acceso denegado: solo puede consultar o gestionar convivientes de su propia unidad");
            }
        }
    }

    private void validatePropertyScope(Long propertyId) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            return;
        }

        String roleCode = ctx.getRoleCode();
        if ("SUPERADMIN".equals(roleCode)) {
            return;
        }

        if ("ADMIN_PROPIEDAD".equals(roleCode) || "PROPIEDAD".equals(ctx.getRoleScope())) {
            Long callerPropId = ctx.getPropertyId();
            if (callerPropId != null && !callerPropId.equals(propertyId)) {
                throw new AccessDeniedException("Acceso denegado: la unidad no pertenece a su propiedad asignada");
            }
        } else if ("ADMIN_ORGANIZACION".equals(roleCode) || "ORGANIZACION".equals(ctx.getRoleScope())) {
            Long callerOrgId = ctx.getOrganizationId();
            if (callerOrgId != null) {
                Integer propInOrg = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM PROPIEDADES WHERE ID_PROPIEDAD = :propId AND ID_ORGANIZACION = :orgId",
                        new MapSqlParameterSource("propId", propertyId).addValue("orgId", callerOrgId),
                        Integer.class
                );
                if (propInOrg == null || propInOrg == 0) {
                    throw new AccessDeniedException("Acceso denegado: la propiedad no pertenece a su organización asignada");
                }
            }
        }
    }
}
