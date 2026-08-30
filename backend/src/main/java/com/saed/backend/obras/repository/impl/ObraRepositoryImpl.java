package com.saed.backend.obras.repository.impl;

import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.repository.ObraRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
public class ObraRepositoryImpl implements ObraRepository {

    private final JdbcTemplate jdbcTemplate;

    public ObraRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ObraDTO> rowMapper = (rs, rowNum) -> {
        ObraDTO dto = new ObraDTO();
        dto.setIdObra(rs.getLong("ID_OBRA"));
        dto.setIdUnidad(rs.getLong("ID_UNIDAD"));
        dto.setDescripcion(rs.getString("DESCRIPCION"));
        
        Date fi = rs.getDate("FECHA_INICIO");
        if (fi != null) dto.setFechaInicio(fi.toLocalDate());
        
        Date ff = rs.getDate("FECHA_FIN_ESTIMADA");
        if (ff != null) dto.setFechaFinEstimada(ff.toLocalDate());
        
        dto.setResponsableObra(rs.getString("RESPONSABLE_OBRA"));
        dto.setTelefonoResponsable(rs.getString("TELEFONO_RESPONSABLE"));
        dto.setDepositoGarantia(rs.getBigDecimal("DEPOSITO_GARANTIA"));
        dto.setLicenciaUrbanisticaUrl(rs.getString("LICENCIA_URBANISTICA_URL"));
        dto.setEstado(rs.getString("ESTADO"));
        
        long sol = rs.getLong("SOLICITADO_POR");
        if (!rs.wasNull()) dto.setSolicitadoPor(sol);
        
        long apr = rs.getLong("APROBADO_POR");
        if (!rs.wasNull()) dto.setAprobadoPor(apr);
        
        Timestamp fa = rs.getTimestamp("FECHA_APROBACION");
        if (fa != null) dto.setFechaAprobacion(fa.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
        
        return dto;
    };

    @Override
    public List<ObraDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = "SELECT O.* FROM OBRAS O " +
                     "JOIN UNIDADES U ON O.ID_UNIDAD = U.ID_UNIDAD " +
                     "WHERE U.ID_PROPIEDAD = ? ORDER BY O.FECHA_INICIO DESC";
        return jdbcTemplate.query(sql, rowMapper, idPropiedad);
    }

    @Override
    public List<ObraDTO> findAllByUnidad(Long idUnidad, Long idPropiedad) {
        String sql = "SELECT O.* FROM OBRAS O " +
                     "JOIN UNIDADES U ON O.ID_UNIDAD = U.ID_UNIDAD " +
                     "WHERE O.ID_UNIDAD = ? AND U.ID_PROPIEDAD = ? ORDER BY O.FECHA_INICIO DESC";
        return jdbcTemplate.query(sql, rowMapper, idUnidad, idPropiedad);
    }

    @Override
    public Optional<ObraDTO> findById(Long idObra, Long idPropiedad) {
        String sql = "SELECT O.* FROM OBRAS O " +
                     "JOIN UNIDADES U ON O.ID_UNIDAD = U.ID_UNIDAD " +
                     "WHERE O.ID_OBRA = ? AND U.ID_PROPIEDAD = ?";
        List<ObraDTO> list = jdbcTemplate.query(sql, rowMapper, idObra, idPropiedad);
        return list.stream().findFirst();
    }

    @Override
    public Long createObra(ObraDTO obra, Long idPropiedad, Long solicitadoPor) {
        // Enforce IDOR on create: Make sure the ID_UNIDAD actually belongs to the ID_PROPIEDAD
        String checkSql = "SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ? AND ID_PROPIEDAD = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, obra.getIdUnidad(), idPropiedad);
        if (count == null || count == 0) {
            throw new SecurityException("Unidad no pertenece a la propiedad actual.");
        }

        String sql = "INSERT INTO OBRAS (ID_UNIDAD, DESCRIPCION, FECHA_INICIO, FECHA_FIN_ESTIMADA, RESPONSABLE_OBRA, " +
                     "TELEFONO_RESPONSABLE, DEPOSITO_GARANTIA, LICENCIA_URBANISTICA_URL, ESTADO, SOLICITADO_POR) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_OBRA"});
            ps.setLong(1, obra.getIdUnidad());
            ps.setString(2, obra.getDescripcion());
            ps.setDate(3, Date.valueOf(obra.getFechaInicio()));
            ps.setDate(4, Date.valueOf(obra.getFechaFinEstimada()));
            ps.setString(5, obra.getResponsableObra());
            ps.setString(6, obra.getTelefonoResponsable());
            ps.setBigDecimal(7, obra.getDepositoGarantia());
            ps.setString(8, obra.getLicenciaUrbanisticaUrl());
            ps.setString(9, "SOLICITADA"); // Default estado
            ps.setLong(10, solicitadoPor);
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    @Override
    public void updateEstado(Long idObra, Long idPropiedad, String estado, Long aprobadoPor) {
        // Must join UNIDADES to enforce property isolation in update
        String sql = "UPDATE OBRAS SET ESTADO = ?, APROBADO_POR = ?, FECHA_APROBACION = SYSTIMESTAMP " +
                     "WHERE ID_OBRA = ? AND ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = ?)";
        int updated = jdbcTemplate.update(sql, estado, aprobadoPor, idObra, idPropiedad);
        if (updated == 0) {
            throw new IllegalArgumentException("Obra no encontrada o sin acceso para actualizar");
        }
    }
}
