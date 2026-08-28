package com.saed.backend.finanzas.repository;

import com.saed.backend.finanzas.dto.*;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ContratoProveedorRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ContratoProveedorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Map<String, Object> tenantParams() {
        return Map.of("propId", SaedContextHolder.getContext().getPropertyId());
    }

    public List<ContratoProveedorDTO> listar() {
        return jdbc.query(
            "SELECT cp.*, pr.RAZON_SOCIAL AS NOMBRE_PROVEEDOR " +
            "FROM CONTRATOS_PROVEEDOR cp " +
            "JOIN PROVEEDORES pr ON cp.ID_PROVEEDOR = pr.ID_PROVEEDOR " +
            "WHERE cp.ID_PROPIEDAD = :propId ORDER BY cp.ID_CONTRATO_PROVEEDOR DESC",
            tenantParams(),
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
                rs.getString("ESTADO")
            )
        );
    }

    public ContratoProveedorDTO crear(ContratoProveedorCreateDTO req) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
            "INSERT INTO CONTRATOS_PROVEEDOR (ID_PROVEEDOR, ID_PROPIEDAD, NUMERO_CONTRATO, OBJETO_CONTRATO, " +
            "VALOR_TOTAL, PERIODICIDAD_PAGO, FECHA_INICIO, FECHA_FIN, DIAS_ALERTA_VENC) " +
            "VALUES (:idProveedor, :propId, :numContrato, :objeto, :valor, :periodicidad, :fInicio, :fFin, :diasAlerta)",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("idProveedor", req.idProveedor())
                .addValue("propId", SaedContextHolder.getContext().getPropertyId())
                .addValue("numContrato", req.numeroContrato())
                .addValue("objeto", req.objetoContrato())
                .addValue("valor", req.valorTotal())
                .addValue("periodicidad", req.periodicidadPago() != null ? req.periodicidadPago() : "MENSUAL")
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
                rs.getLong("ID_CONTRATO_PROVEEDOR"), rs.getLong("ID_PROVEEDOR"), rs.getLong("ID_PROPIEDAD"),
                rs.getString("NUMERO_CONTRATO"), rs.getString("OBJETO_CONTRATO"), rs.getBigDecimal("VALOR_TOTAL"),
                rs.getString("PERIODICIDAD_PAGO"),
                rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
                rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
                rs.getInt("DIAS_ALERTA_VENC"), rs.getString("ESTADO")
            )
        );
    }

    public void actualizarEstado(Long id, String estado) {
        jdbc.update("UPDATE CONTRATOS_PROVEEDOR SET ESTADO = :estado WHERE ID_CONTRATO_PROVEEDOR = :id",
            Map.of("id", id, "estado", estado));
    }

    public void eliminar(Long id) {
        jdbc.update("DELETE FROM CONTRATOS_PROVEEDOR WHERE ID_CONTRATO_PROVEEDOR = :id", Map.of("id", id));
    }
}
