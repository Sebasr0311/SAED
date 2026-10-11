package com.saed.backend.mantenimiento.plan;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class PlanMantenimientoRepositoryImpl implements PlanMantenimientoRepository {

    private static final String SELECT = """
            SELECT ID_PLAN_MANTENIMIENTO, ID_PROPIEDAD, ID_ACTIVO, TITULO, DESCRIPCION_TRABAJO, TIPO_MANTENIMIENTO,
                   PRIORIDAD, FRECUENCIA, FECHA_INICIO, COSTO_ESTIMADO, ID_PROVEEDOR_SERVICIO, ACTIVO
            FROM PLANES_MANTENIMIENTO
            WHERE ID_PROPIEDAD = :idPropiedad
            """;

    private static final RowMapper<PlanMantenimientoDTO> MAPPER = (rs, n) -> {
        long idActivo = rs.getLong("ID_ACTIVO");
        Long activo = rs.wasNull() ? null : idActivo;
        long idProveedor = rs.getLong("ID_PROVEEDOR_SERVICIO");
        Long proveedor = rs.wasNull() ? null : idProveedor;
        Date inicio = rs.getDate("FECHA_INICIO");
        return new PlanMantenimientoDTO(rs.getLong("ID_PLAN_MANTENIMIENTO"), rs.getLong("ID_PROPIEDAD"), activo,
                rs.getString("TITULO"), rs.getString("DESCRIPCION_TRABAJO"), rs.getString("TIPO_MANTENIMIENTO"),
                rs.getString("PRIORIDAD"), rs.getString("FRECUENCIA"), inicio != null ? inicio.toLocalDate() : null,
                rs.getBigDecimal("COSTO_ESTIMADO"), proveedor, rs.getString("ACTIVO"));
    };

    private final NamedParameterJdbcTemplate jdbc;

    public PlanMantenimientoRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Long crear(PlanMantenimientoDTO plan, Long idPropiedad, Long creadoPor) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO PLANES_MANTENIMIENTO (ID_PROPIEDAD, ID_ACTIVO, TITULO, DESCRIPCION_TRABAJO,
                    TIPO_MANTENIMIENTO, PRIORIDAD, FRECUENCIA, FECHA_INICIO, COSTO_ESTIMADO, ID_PROVEEDOR_SERVICIO,
                    ACTIVO, CREADO_POR)
                VALUES (:idPropiedad, :idActivo, :titulo, :descripcion, :tipo, :prioridad, :frecuencia, :inicio,
                    :costo, :idProveedor, 'S', :creadoPor)
                """, new MapSqlParameterSource()
                .addValue("idPropiedad", idPropiedad)
                .addValue("idActivo", plan.idActivo())
                .addValue("titulo", plan.titulo())
                .addValue("descripcion", plan.descripcionTrabajo())
                .addValue("tipo", plan.tipoMantenimiento())
                .addValue("prioridad", plan.prioridad())
                .addValue("frecuencia", plan.frecuencia())
                .addValue("inicio", Date.valueOf(plan.fechaInicio()))
                .addValue("costo", plan.costoEstimado())
                .addValue("idProveedor", plan.idProveedorServicio())
                .addValue("creadoPor", creadoPor), keys, new String[]{"ID_PLAN_MANTENIMIENTO"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("The maintenance plan id was not generated.");
        }
        return key.longValue();
    }

    @Override
    public List<PlanMantenimientoDTO> listar(Long idPropiedad) {
        return jdbc.query(SELECT + " ORDER BY ID_PLAN_MANTENIMIENTO DESC",
                new MapSqlParameterSource("idPropiedad", idPropiedad), MAPPER);
    }

    @Override
    public List<PlanMantenimientoDTO> listarActivos(Long idPropiedad) {
        return jdbc.query(SELECT + " AND ACTIVO = 'S' ORDER BY ID_PLAN_MANTENIMIENTO",
                new MapSqlParameterSource("idPropiedad", idPropiedad), MAPPER);
    }

    @Override
    public int desactivar(Long idPlan, Long idPropiedad) {
        return jdbc.update("UPDATE PLANES_MANTENIMIENTO SET ACTIVO = 'N' "
                        + "WHERE ID_PLAN_MANTENIMIENTO = :idPlan AND ID_PROPIEDAD = :idPropiedad",
                new MapSqlParameterSource("idPlan", idPlan).addValue("idPropiedad", idPropiedad));
    }

    @Override
    public boolean existeOrden(Long idPlan, String periodo) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM MANTENIMIENTOS "
                        + "WHERE ID_PLAN_MANTENIMIENTO = :idPlan AND PERIODO_PLAN = :periodo",
                new MapSqlParameterSource("idPlan", idPlan).addValue("periodo", periodo), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Long crearOrden(PlanMantenimientoDTO plan, LocalDate fechaProgramada, String periodo) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO MANTENIMIENTOS (ID_ACTIVO, ID_PROPIEDAD, TIPO_MANTENIMIENTO, PRIORIDAD, TITULO,
                    DESCRIPCION_TRABAJO, FECHA_PROGRAMADA, COSTO_ESTIMADO, ID_PROVEEDOR_SERVICIO, ESTADO,
                    ID_PLAN_MANTENIMIENTO, PERIODO_PLAN)
                VALUES (:idActivo, :idPropiedad, :tipo, :prioridad, :titulo, :descripcion, :fecha, :costo,
                    :idProveedor, 'PROGRAMADO', :idPlan, :periodo)
                """, new MapSqlParameterSource()
                .addValue("idActivo", plan.idActivo())
                .addValue("idPropiedad", plan.idPropiedad())
                .addValue("tipo", plan.tipoMantenimiento())
                .addValue("prioridad", plan.prioridad())
                .addValue("titulo", plan.titulo())
                .addValue("descripcion", plan.descripcionTrabajo())
                .addValue("fecha", Date.valueOf(fechaProgramada))
                .addValue("costo", plan.costoEstimado())
                .addValue("idProveedor", plan.idProveedorServicio())
                .addValue("idPlan", plan.idPlan())
                .addValue("periodo", periodo), keys, new String[]{"ID_MANTENIMIENTO"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("The maintenance order id was not generated.");
        }
        return key.longValue();
    }
}
