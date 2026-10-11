package com.saed.backend.finanzas.repository;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.ContratoProveedorCreateDTO;
import com.saed.backend.finanzas.dto.ContratoProveedorDTO;
import com.saed.backend.proveedores.contratos.ContratoProveedorValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Repositorio de Contratos de Proveedor (GAP-F9-01).
 * Implementa validaciones estrictas anti-cross-tenant entre PROPIEDAD y PROVEEDOR.
 */
@Repository
public class ContratoProveedorRepository {

    private static final Logger log = LoggerFactory.getLogger(ContratoProveedorRepository.class);

    /** CK_CONTPROV_ESTADO. */
    private static final Set<String> ESTADOS = Set.of("VIGENTE", "VENCIDO", "TERMINADO", "SUSPENDIDO");

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
        Long propId = resolveActivePropertyId();

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("propId", propId);
        StringBuilder sql = new StringBuilder("""
            SELECT cp.*, pr.RAZON_SOCIAL AS NOMBRE_PROVEEDOR
            FROM CONTRATOS_PROVEEDOR cp
            JOIN PROVEEDORES pr ON cp.ID_PROVEEDOR = pr.ID_PROVEEDOR
            WHERE cp.ID_PROPIEDAD = :propId
            """);

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
        // Business validation first (required fields, dates, value, alert days), before any database work.
        ContratoProveedorValidator.validar(req);
        Long propId = resolveActivePropertyId();

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
            throw new NoSuchElementException("Proveedor no encontrado: " + req.idProveedor());
        }

        Map<String, Object> check = rows.get(0);
        Long provOrgId = ((Number) check.get("PROV_ORG_ID")).longValue();
        Long propOrgId = ((Number) check.get("PROP_ORG_ID")).longValue();
        String provEstado = (String) check.get("PROV_ESTADO");

        // VALIDACIÓN CRÍTICA CROSS-TENANT (GAP-F9-01)
        if (!provOrgId.equals(propOrgId)) {
            log.warn("[Cross-Tenant Attack Blocked] Intento de vincular proveedor {} (Org {}) con propiedad {} (Org {})",
                    req.idProveedor(), provOrgId, propId, propOrgId);
            // Indistinguishable from a missing supplier: never reveal that it exists in another organization.
            throw new NoSuchElementException("Proveedor no encontrado: " + req.idProveedor());
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
        String target = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(target)) {
            throw new IllegalArgumentException("Estado de contrato no valido. Permitidos: VIGENTE, VENCIDO, TERMINADO, SUSPENDIDO");
        }
        Long propId = resolveActivePropertyId();
        int updated = jdbc.update(
            "UPDATE CONTRATOS_PROVEEDOR SET ESTADO = :estado WHERE ID_CONTRATO_PROVEEDOR = :id AND ID_PROPIEDAD = :propId",
            Map.of("id", id, "estado", target, "propId", propId)
        );
        if (updated == 0) {
            throw new NoSuchElementException("Contrato de proveedor no encontrado: " + id);
        }
    }

    public void eliminar(Long id) {
        Long propId = resolveActivePropertyId();
        int deleted = jdbc.update(
            "DELETE FROM CONTRATOS_PROVEEDOR WHERE ID_CONTRATO_PROVEEDOR = :id AND ID_PROPIEDAD = :propId",
            Map.of("id", id, "propId", propId)
        );
        if (deleted == 0) {
            throw new NoSuchElementException("Contrato de proveedor no encontrado: " + id);
        }
    }
}
