package com.saed.backend.platform.service.impl;

import com.saed.backend.audit.AuditService;
import com.saed.backend.common.exception.StorageQuotaExceededException;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.dto.StorageQuotaDTO;
import com.saed.backend.platform.exception.InactiveMembershipException;
import com.saed.backend.platform.service.StorageQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class StorageQuotaServiceImpl implements StorageQuotaService {

    private static final Logger log = LoggerFactory.getLogger(StorageQuotaServiceImpl.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public StorageQuotaServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Override
    public long getStorageLimitBytes(Long organizationId) {
        if (organizationId == null) {
            throw new AccessDeniedException("Identificador de organización requerido para consultar cuota de almacenamiento");
        }
        ActiveStoragePlan plan = resolveActiveStoragePlan(organizationId, false, "consultar límite de almacenamiento");
        return convertGbToBytes(plan.getLimiteAlmacenamientoGb());
    }

    @Override
    public long getStorageUsedBytes(Long organizationId) {
        if (organizationId == null) {
            throw new AccessDeniedException("Identificador de organización requerido para consultar uso de almacenamiento");
        }

        long docBytes = 0L;
        try {
            String docSql = """
                SELECT COALESCE(SUM(v.ARCHIVO_TAMANO_BYTES), 0)
                FROM VERSIONES_DOCUMENTO v
                JOIN DOCUMENTOS d ON v.ID_DOCUMENTO = d.ID_DOCUMENTO
                WHERE d.ID_ORGANIZACION = :orgId
                  AND (d.ESTADO IS NULL OR d.ESTADO != 'ELIMINADO')
            """;
            Number num = jdbcTemplate.queryForObject(docSql, new MapSqlParameterSource("orgId", organizationId), Number.class);
            if (num != null) docBytes = num.longValue();
        } catch (Exception e) {
            log.warn("Error consultando almacenamiento en VERSIONES_DOCUMENTO: {}", e.getMessage());
        }

        long gastosBytes = 0L;
        try {
            String gastosSql = """
                SELECT COALESCE(SUM(g.ARCHIVO_TAMANO_BYTES), 0)
                FROM GASTOS g
                JOIN PROPIEDADES p ON g.ID_PROPIEDAD = p.ID_PROPIEDAD
                WHERE p.ID_ORGANIZACION = :orgId
                  AND (g.ESTADO IS NULL OR g.ESTADO != 'ANULADO')
            """;
            Number num = jdbcTemplate.queryForObject(gastosSql, new MapSqlParameterSource("orgId", organizationId), Number.class);
            if (num != null) gastosBytes = num.longValue();
        } catch (Exception ignored) {
            // Esquema sin columna ARCHIVO_TAMANO_BYTES en GASTOS (baseline)
        }

        return Math.max(0L, docBytes + gastosBytes);
    }

    @Override
    public long getStorageAvailableBytes(Long organizationId) {
        long limit = getStorageLimitBytes(organizationId);
        long used = getStorageUsedBytes(organizationId);
        return Math.max(0L, limit - used);
    }

    @Override
    public StorageQuotaDTO getQuota(Long organizationId) {
        if (organizationId == null) {
            throw new AccessDeniedException("Identificador de organización requerido para consultar cuota de almacenamiento");
        }

        ActiveStoragePlan plan = resolveActiveStoragePlan(organizationId, false, "consultar cuota de almacenamiento");
        long limitBytes = convertGbToBytes(plan.getLimiteAlmacenamientoGb());
        long usedBytes = getStorageUsedBytes(organizationId);
        long availableBytes = Math.max(0L, limitBytes - usedBytes);

        double limitGb = roundGb(limitBytes);
        double usedGb = roundGb(usedBytes);
        double availableGb = roundGb(availableBytes);
        double percentageUsed = limitBytes > 0
                ? Math.min(100.0, Math.round((double) usedBytes / limitBytes * 10000.0) / 100.0)
                : 0.0;

        return new StorageQuotaDTO(
                organizationId,
                limitBytes,
                usedBytes,
                availableBytes,
                limitGb,
                usedGb,
                availableGb,
                percentageUsed,
                plan.getPlanCodigo(),
                plan.getEstado()
        );
    }

    @Override
    @Transactional
    public void validateUpload(Long organizationId, long newFileBytes) {
        if (organizationId == null) {
            throw new AccessDeniedException("Identificador de organización requerido para validar cuota de almacenamiento");
        }
        if (newFileBytes < 0) {
            throw new IllegalArgumentException("El tamaño del archivo no puede ser negativo");
        }
        if (newFileBytes > MAX_SINGLE_FILE_BYTES) {
            throw new IllegalArgumentException(String.format("El archivo (%d bytes) excede el tamaño máximo individual permitido de %d bytes (10 MB)",
                    newFileBytes, MAX_SINGLE_FILE_BYTES));
        }

        // 1. Pessimistic lock on the active membership to serialize multi-instance concurrency
        ActiveStoragePlan plan = resolveActiveStoragePlan(organizationId, true, "subir archivos");
        long limitBytes = convertGbToBytes(plan.getLimiteAlmacenamientoGb());
        long usedBytes = getStorageUsedBytes(organizationId);

        // 2. Strict limit validation: used + new > limit is rejected; exact match used + new == limit passes
        if (usedBytes + newFileBytes > limitBytes) {
            log.warn("Cuota de almacenamiento excedida para organización {}: usado={} bytes, nuevo={} bytes, límite={} bytes",
                    organizationId, usedBytes, newFileBytes, limitBytes);

            recordAuditQuotaExceeded(organizationId, "UPLOAD_EXCEEDED", limitBytes, usedBytes, newFileBytes);

            throw new StorageQuotaExceededException(limitBytes, usedBytes, newFileBytes);
        }
    }

    @Override
    @Transactional
    public void validateReplacement(Long organizationId, long oldFileBytes, long newFileBytes) {
        if (organizationId == null) {
            throw new AccessDeniedException("Identificador de organización requerido para validar reemplazo de archivo");
        }
        if (newFileBytes < 0) {
            throw new IllegalArgumentException("El tamaño del archivo de reemplazo no puede ser negativo");
        }
        if (newFileBytes > MAX_SINGLE_FILE_BYTES) {
            throw new IllegalArgumentException(String.format("El archivo de reemplazo (%d bytes) excede el tamaño máximo individual permitido de %d bytes (10 MB)",
                    newFileBytes, MAX_SINGLE_FILE_BYTES));
        }

        // 1. Pessimistic lock on the active membership to serialize multi-instance concurrency
        ActiveStoragePlan plan = resolveActiveStoragePlan(organizationId, true, "reemplazar archivos");
        long limitBytes = convertGbToBytes(plan.getLimiteAlmacenamientoGb());
        long usedBytes = getStorageUsedBytes(organizationId);

        // 2. Net change calculation: used - oldFileBytes + newFileBytes
        long safeOld = Math.max(0L, oldFileBytes);
        long projectedUsed = Math.max(0L, usedBytes - safeOld) + newFileBytes;

        if (projectedUsed > limitBytes) {
            log.warn("Cuota de almacenamiento excedida al reemplazar archivo para organización {}: usado={} bytes, liberado={} bytes, nuevo={} bytes, proyectado={} bytes, límite={} bytes",
                    organizationId, usedBytes, safeOld, newFileBytes, projectedUsed, limitBytes);

            recordAuditQuotaExceeded(organizationId, "REPLACEMENT_EXCEEDED", limitBytes, usedBytes, newFileBytes);

            throw new StorageQuotaExceededException(limitBytes, usedBytes, newFileBytes);
        }
    }

    private ActiveStoragePlan resolveActiveStoragePlan(Long organizationId, boolean forUpdate, String action) {
        String baseSql = """
            SELECT m.ID_MEMBRESIA, m.ID_PLAN, m.ESTADO, p.CODIGO AS PLAN_CODIGO,
                   p.LIMITE_ALMACENAMIENTO_GB
            FROM MEMBRESIAS m
            JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN
            WHERE m.ID_ORGANIZACION = :orgId
              AND m.ESTADO IN ('ACTIVA', 'PRUEBA')
              AND (m.FECHA_FIN IS NULL OR m.FECHA_FIN >= TRUNC(SYSDATE))
        """;

        String querySql = forUpdate ? baseSql + " FOR UPDATE OF m.ID_MEMBRESIA" : baseSql + " ORDER BY m.FECHA_INICIO DESC";

        List<ActiveStoragePlan> rows = jdbcTemplate.query(
                querySql,
                new MapSqlParameterSource("orgId", organizationId),
                (rs, rowNum) -> new ActiveStoragePlan(
                        rs.getLong("ID_MEMBRESIA"),
                        rs.getLong("ID_PLAN"),
                        rs.getString("ESTADO"),
                        rs.getString("PLAN_CODIGO"),
                        rs.getObject("LIMITE_ALMACENAMIENTO_GB") != null
                                ? BigDecimal.valueOf(rs.getDouble("LIMITE_ALMACENAMIENTO_GB"))
                                : null
                )
        );

        if (!rows.isEmpty()) {
            return rows.get(0);
        }

        // Inspect existing membership records to produce precise fail-closed diagnostic
        String inspectSql = """
            SELECT m.ESTADO, m.FECHA_FIN
            FROM MEMBRESIAS m
            WHERE m.ID_ORGANIZACION = :orgId
            ORDER BY m.FECHA_INICIO DESC
        """;
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                inspectSql,
                new MapSqlParameterSource("orgId", organizationId)
        );

        if (!existing.isEmpty()) {
            String estado = (String) existing.get(0).get("ESTADO");
            throw new InactiveMembershipException(
                    String.format("La membresía de la organización no está vigente (estado: %s). No se puede %s.",
                            estado != null ? estado : "INACTIVA", action)
            );
        }

        throw new InactiveMembershipException(
                String.format("La organización no cuenta con una suscripción a ningún plan SaaS. No se puede %s.", action)
        );
    }

    private long convertGbToBytes(BigDecimal gb) {
        if (gb == null || gb.compareTo(BigDecimal.ZERO) <= 0) {
            // Fail-closed policy: plans with null or <= 0 storage limit have 0 bytes allocated
            return 0L;
        }
        return gb.multiply(BigDecimal.valueOf(BYTES_PER_GB)).longValue();
    }

    private double roundGb(long bytes) {
        return BigDecimal.valueOf(bytes)
                .divide(BigDecimal.valueOf(BYTES_PER_GB), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void recordAuditQuotaExceeded(Long organizationId, String action, long limit, long used, long requested) {
        try {
            SaedContext ctx = SaedContextHolder.getContext();
            Long userId = ctx != null ? ctx.getUserId() : null;
            Long propId = ctx != null ? ctx.getPropertyId() : null;

            String details = String.format("{\"action\":\"%s\",\"limitBytes\":%d,\"usedBytes\":%d,\"requestedBytes\":%d}",
                    action, limit, used, requested);

            auditService.recordFailure(
                    userId,
                    organizationId,
                    propId,
                    "ACCESO_DENEGADO",
                    "STORAGE",
                    organizationId,
                    null,
                    null,
                    null,
                    details
            );
        } catch (Exception e) {
            log.warn("No se pudo registrar evento de auditoría para cuota excedida: {}", e.getMessage());
        }
    }

    private static class ActiveStoragePlan {
        private final Long idMembresia;
        private final Long idPlan;
        private final String estado;
        private final String planCodigo;
        private final BigDecimal limiteAlmacenamientoGb;

        ActiveStoragePlan(Long idMembresia, Long idPlan, String estado,
                          String planCodigo, BigDecimal limiteAlmacenamientoGb) {
            this.idMembresia = idMembresia;
            this.idPlan = idPlan;
            this.estado = estado;
            this.planCodigo = planCodigo;
            this.limiteAlmacenamientoGb = limiteAlmacenamientoGb;
        }

        public Long getIdMembresia() {
            return idMembresia;
        }

        public Long getIdPlan() {
            return idPlan;
        }

        public String getEstado() {
            return estado;
        }

        public String getPlanCodigo() {
            return planCodigo;
        }

        public BigDecimal getLimiteAlmacenamientoGb() {
            return limiteAlmacenamientoGb;
        }
    }
}
