package com.saed.backend.finanzas.repository;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.ContratoProveedorCreateDTO;
import com.saed.backend.finanzas.dto.ContratoProveedorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Repositorio de Contratos de Proveedor (GAP-F9-01).
 * Implementa validaciones estrictas anti-cross-tenant entre PROPIEDAD y PROVEEDOR.
 */
@Repository
public class ContratoProveedorRepository {

    private static final Logger log = LoggerFactory.getLogger(ContratoProveedorRepository.class);

    private final NamedParameterJdbcTemplate jdbc;

    public ContratoProveedorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Long resolveActivePropertyId() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            throw new AccessDeniedException("Se requiere una propiedad activa en la sesión para gestionar contratos de proveedor");
        }
        return ctx.getPropertyId();
    }

    public List<ContratoProveedorDTO> listar() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = (ctx != null) ? ctx.getPropertyId() : null;

        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("""
            SELECT cp.*, pr.RAZON_SOCIAL AS NOMBRE_PROVEEDOR
            FROM CONTRATOS_PROVEEDOR cp
            JOIN PROVEEDORES pr ON cp.ID_PROVEEDOR = pr.ID_PROVEEDOR
            """);

        if (propId != null) {
            sql.append(" WHERE cp.ID_PROPIEDAD = :propId");
            params.addValue("propId", propId);
        }

        sql.append(" ORDER BY cp.ID_CONTRATO_PROVEEDOR DESC");

        return jdbc.query(
            sql.toString(),
            params,
            (rs, rowNum) -> new ContratoProveedorDTO(
                rs.getLong("ID_CONTRATO_PROVEEDOR"),
                rs.getLong("ID_PROVEEDOR"),
                rs.getLong("ID_PROPIEDAD"),
                rs.getString("NUMERO_CONTRATO"),
                rs.getString("OBJETO_CONTRATO"),
                rs.getBigDecimal("VALOR_TOTAL"),
                rs.getString("PERIODICIDAD_PAGO"),
                rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
                rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
                rs.getInt("DIAS_ALERTA_VENC"),
                rs.getString("ESTADO"),
                rs.getString("NOMBRE_PROVEEDOR")
            )
        );
    }

    public ContratoProveedorDTO crear(ContratoProveedorCreateDTO req) {
        Long propId = resolveActivePropertyId();

        if (req.idProveedor() == null) {
            throw new IllegalArgumentException("El ID del proveedor es obligatorio");
        }

        if (req.fechaInicio() != null && req.fechaFin() != null && req.fechaFin().isBefore(req.fechaInicio())) {
            throw new IllegalArgumentException("La fecha de finalización no puede ser anterior a la fecha de inicio (CK_CONTPROV_FECHAS)");
        }

        // 1. Validar existencia del proveedor y correspondencia tenant con la propiedad activa
        String checkSql = """
            SELECT p.ID_PROVEEDOR, p.ID_ORGANIZACION AS PROV_ORG_ID, p.ESTADO AS PROV_ESTADO,
                   pr.ID_ORGANIZACION AS PROP_ORG_ID
            FROM PROVEEDORES p
            CROSS JOIN PROPIEDADES pr
            WHERE p.ID_PROVEEDOR = :idProveedor
              AND pr.ID_PROPIEDAD = :propId
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(
            checkSql,
            new MapSqlParameterSource()
                .addValue("idProveedor", req.idProveedor())
                .addValue("propId", propId)
        );

        if (rows.isEmpty()) {
            throw new AccessDeniedException(
                "Violación de aislamiento multi-tenant: El proveedor " + req.idProveedor() +
                " no está autorizado o pertenece a otra organización para la propiedad " + propId + "."
            );
        }

        Map<String, Object> check = rows.get(0);
        Long provOrgId = ((Number) check.get("PROV_ORG_ID")).longValue();
        Long propOrgId = ((Number) check.get("PROP_ORG_ID")).longValue();
        String provEstado = (String) check.get("PROV_ESTADO");

        // VALIDACIÓN CRÍTICA CROSS-TENANT (GAP-F9-01)
        if (!provOrgId.equals(propOrgId)) {
            log.warn("[Cross-Tenant Attack Blocked] Intento de vincular proveedor {} (Org {}) con propiedad {} (Org {})",
                    req.idProveedor(), provOrgId, propId, propOrgId);
            throw new AccessDeniedException(
                "Violación de aislamiento multi-tenant: El proveedor " + req.idProveedor() +
                " pertenece a la organización " + provOrgId + " y no puede ser vinculado a la propiedad " +
                propId + " perteneciente a la organización " + propOrgId + "."
            );
        }

        // VALIDACIÓN DE ESTADO DEL PROVEEDOR
        if (!"ACTIVO".equalsIgnoreCase(provEstado)) {
            throw new IllegalArgumentException(
                "El proveedor seleccionado se encuentra en estado '" + provEstado +
                "'. Solo es posible asociar contratos a proveedores en estado ACTIVO."
            );
        }

        // VALIDACIÓN DE UNICIDAD DE NÚMERO DE CONTRATO POR PROPIEDAD (UQ_CONTPROV_NUM)
        Number countContrato = jdbc.queryForObject(
            "SELECT COUNT(*) FROM CONTRATOS_PROVEEDOR WHERE ID_PROPIEDAD = :propId AND UPPER(NUMERO_CONTRATO) = UPPER(:numContrato)",
            new MapSqlParameterSource()
                .addValue("propId", propId)
                .addValue("numContrato", req.numeroContrato().trim()),
            Number.class
        );
        if (countContrato != null && countContrato.intValue() > 0) {
            throw new IllegalArgumentException(
                "Ya existe un contrato registrado con el número '" + req.numeroContrato() + "' en esta propiedad."
            );
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
            "INSERT INTO CONTRATOS_PROVEEDOR (ID_PROVEEDOR, ID_PROPIEDAD, NUMERO_CONTRATO, OBJETO_CONTRATO, " +
            "VALOR_TOTAL, PERIODICIDAD_PAGO, FECHA_INICIO, FECHA_FIN, DIAS_ALERTA_VENC) " +
            "VALUES (:idProveedor, :propId, :numContrato, :objeto, :valor, :periodicidad, :fInicio, :fFin, :diasAlerta)",
            new MapSqlParameterSource()
                .addValue("idProveedor", req.idProveedor())
                .addValue("propId", propId)
                .addValue("numContrato", req.numeroContrato().trim())
                .addValue("objeto", req.objetoContrato().trim())
                .addValue("valor", req.valorTotal())
                .addValue("periodicidad", req.periodicidadPago() != null ? req.periodicidadPago().trim().toUpperCase() : "MENSUAL")
                .addValue("fInicio", req.fechaInicio())
                .addValue("fFin", req.fechaFin())
                .addValue("diasAlerta", req.diasAlertaVenc() != null ? req.diasAlertaVenc() : 30),
            keyHolder,
            new String[]{"ID_CONTRATO_PROVEEDOR"}
        );

        Long id = keyHolder.getKey().longValue();
        return jdbc.queryForObject(
            "SELECT cp.*, pr.RAZON_SOCIAL AS NOMBRE_PROVEEDOR " +
            "FROM CONTRATOS_PROVEEDOR cp JOIN PROVEEDORES pr ON cp.ID_PROVEEDOR = pr.ID_PROVEEDOR " +
            "WHERE cp.ID_CONTRATO_PROVEEDOR = :id",
            Map.of("id", id),
            (rs, rowNum) -> new ContratoProveedorDTO(
                rs.getLong("ID_CONTRATO_PROVEEDOR"),
                rs.getLong("ID_PROVEEDOR"),
                rs.getLong("ID_PROPIEDAD"),
                rs.getString("NUMERO_CONTRATO"),
                rs.getString("OBJETO_CONTRATO"),
                rs.getBigDecimal("VALOR_TOTAL"),
                rs.getString("PERIODICIDAD_PAGO"),
                rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
                rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
                rs.getInt("DIAS_ALERTA_VENC"),
                rs.getString("ESTADO"),
                rs.getString("NOMBRE_PROVEEDOR")
            )
        );
    }

    public void actualizarEstado(Long id, String estado) {
        Long propId = resolveActivePropertyId();
        int updated = jdbc.update(
            "UPDATE CONTRATOS_PROVEEDOR SET ESTADO = :estado WHERE ID_CONTRATO_PROVEEDOR = :id AND ID_PROPIEDAD = :propId",
            Map.of("id", id, "estado", estado, "propId", propId)
        );
        if (updated == 0) {
            throw new AccessDeniedException("Contrato no encontrado o no pertenece a la propiedad activa");
        }
    }

    public void eliminar(Long id) {
        Long propId = resolveActivePropertyId();
        int deleted = jdbc.update(
            "DELETE FROM CONTRATOS_PROVEEDOR WHERE ID_CONTRATO_PROVEEDOR = :id AND ID_PROPIEDAD = :propId",
            Map.of("id", id, "propId", propId)
        );
        if (deleted == 0) {
            throw new AccessDeniedException("Contrato no encontrado o no pertenece a la propiedad activa");
        }
    }
}
