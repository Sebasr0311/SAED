package com.saed.backend.proveedores.contratos;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class AvisoRenovacionRepositoryImpl implements AvisoRenovacionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AvisoRenovacionRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ContratoPorVencerDTO> contratosPorVencer(Long idPropiedad, LocalDate fecha) {
        return jdbc.query("""
                SELECT cp.ID_CONTRATO_PROVEEDOR, cp.ID_PROPIEDAD, cp.NUMERO_CONTRATO, pr.RAZON_SOCIAL,
                       cp.FECHA_FIN, cp.DIAS_ALERTA_VENC
                FROM CONTRATOS_PROVEEDOR cp
                JOIN PROVEEDORES pr ON pr.ID_PROVEEDOR = cp.ID_PROVEEDOR
                WHERE cp.ID_PROPIEDAD = :idPropiedad
                  AND cp.ESTADO = 'VIGENTE'
                  AND cp.FECHA_FIN >= :fecha
                  AND cp.FECHA_FIN - cp.DIAS_ALERTA_VENC <= :fecha
                ORDER BY cp.FECHA_FIN
                """,
                new MapSqlParameterSource("idPropiedad", idPropiedad).addValue("fecha", Date.valueOf(fecha)),
                (rs, n) -> new ContratoPorVencerDTO(rs.getLong("ID_CONTRATO_PROVEEDOR"), rs.getLong("ID_PROPIEDAD"),
                        rs.getString("NUMERO_CONTRATO"), rs.getString("RAZON_SOCIAL"),
                        rs.getDate("FECHA_FIN").toLocalDate(), rs.getInt("DIAS_ALERTA_VENC")));
    }

    @Override
    public boolean existeAviso(Long idContratoProveedor, String periodo) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM AVISOS_RENOVACION_CONTRATO "
                        + "WHERE ID_CONTRATO_PROVEEDOR = :id AND PERIODO_REFERENCIA = :periodo",
                new MapSqlParameterSource("id", idContratoProveedor).addValue("periodo", periodo), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Long registrarAviso(ContratoPorVencerDTO contrato, String periodo) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO AVISOS_RENOVACION_CONTRATO (ID_PROPIEDAD, ID_CONTRATO_PROVEEDOR, PERIODO_REFERENCIA, FECHA_FIN)
                VALUES (:idPropiedad, :idContrato, :periodo, :fechaFin)
                """, new MapSqlParameterSource()
                .addValue("idPropiedad", contrato.idPropiedad())
                .addValue("idContrato", contrato.idContratoProveedor())
                .addValue("periodo", periodo)
                .addValue("fechaFin", Date.valueOf(contrato.fechaFin())), keys, new String[]{"ID_AVISO_RENOVACION"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("The renewal notice id was not generated.");
        }
        return key.longValue();
    }

    @Override
    public void crearAlertaAdmin(Long idPropiedad, String mensaje) {
        jdbc.update("INSERT INTO ALERTAS_ADMIN (ID_PROPIEDAD, TIPO_ALERTA, MENSAJE) "
                        + "VALUES (:idPropiedad, 'VENCIMIENTO_CONTRATO_PROVEEDOR', :mensaje)",
                new MapSqlParameterSource("idPropiedad", idPropiedad)
                        .addValue("mensaje", mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje));
    }
}
