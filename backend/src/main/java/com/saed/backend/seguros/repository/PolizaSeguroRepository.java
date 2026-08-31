package com.saed.backend.seguros.repository;

import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class PolizaSeguroRepository {
    private final JdbcTemplate jdbcTemplate;

    public PolizaSeguroRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PolizaSeguroDTO> rowMapper = new RowMapper<PolizaSeguroDTO>() {
        @Override
        public PolizaSeguroDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            PolizaSeguroDTO dto = new PolizaSeguroDTO();
            dto.setIdPoliza(rs.getLong("ID_POLIZA"));
            dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
            dto.setCompaniaAseguradora(rs.getString("COMPANIA_ASEGURADORA"));
            dto.setNumeroPoliza(rs.getString("NUMERO_POLIZA"));
            dto.setRamoCobertura(rs.getString("RAMO_COBERTURA"));
            dto.setValorAsegurado(rs.getBigDecimal("VALOR_ASEGURADO"));
            dto.setValorPrimaAnual(rs.getBigDecimal("VALOR_PRIMA_ANUAL"));
            if (rs.getDate("FECHA_INICIO") != null) {
                dto.setFechaInicio(rs.getDate("FECHA_INICIO").toLocalDate());
            }
            if (rs.getDate("FECHA_FIN") != null) {
                dto.setFechaFin(rs.getDate("FECHA_FIN").toLocalDate());
            }
            dto.setDiasAlertaVencimiento(rs.getInt("DIAS_ALERTA_VENCIMIENTO"));
            dto.setNombreCorredorAgente(rs.getString("NOMBRE_CORREDOR_AGENTE"));
            dto.setTelefonoContactoAgente(rs.getString("TELEFONO_CONTACTO_AGENTE"));
            dto.setDocumentoCaratulaUrl(rs.getString("DOCUMENTO_CARATULA_URL"));
            dto.setEstado(rs.getString("ESTADO"));
            return dto;
        }
    };

    public List<PolizaSeguroDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = "SELECT * FROM POLIZAS_SEGURO WHERE ID_PROPIEDAD = ?";
        return jdbcTemplate.query(sql, rowMapper, idPropiedad);
    }
    
    public Optional<PolizaSeguroDTO> findByIdAndPropiedad(Long idPoliza, Long idPropiedad) {
        String sql = "SELECT * FROM POLIZAS_SEGURO WHERE ID_POLIZA = ? AND ID_PROPIEDAD = ?";
        List<PolizaSeguroDTO> list = jdbcTemplate.query(sql, rowMapper, idPoliza, idPropiedad);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insert(PolizaSeguroDTO dto) {
        String sql = "INSERT INTO POLIZAS_SEGURO (ID_PROPIEDAD, COMPANIA_ASEGURADORA, NUMERO_POLIZA, RAMO_COBERTURA, " +
                     "VALOR_ASEGURADO, VALOR_PRIMA_ANUAL, FECHA_INICIO, FECHA_FIN, DIAS_ALERTA_VENCIMIENTO, " +
                     "NOMBRE_CORREDOR_AGENTE, TELEFONO_CONTACTO_AGENTE, DOCUMENTO_CARATULA_URL, ESTADO) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, dto.getIdPropiedad(), dto.getCompaniaAseguradora(), dto.getNumeroPoliza(),
                dto.getRamoCobertura(), dto.getValorAsegurado(), dto.getValorPrimaAnual(),
                dto.getFechaInicio(), dto.getFechaFin(), dto.getDiasAlertaVencimiento(),
                dto.getNombreCorredorAgente(), dto.getTelefonoContactoAgente(), dto.getDocumentoCaratulaUrl(), dto.getEstado());
    }

    public void update(PolizaSeguroDTO dto) {
        String sql = "UPDATE POLIZAS_SEGURO SET COMPANIA_ASEGURADORA = ?, NUMERO_POLIZA = ?, RAMO_COBERTURA = ?, " +
                     "VALOR_ASEGURADO = ?, VALOR_PRIMA_ANUAL = ?, FECHA_INICIO = ?, FECHA_FIN = ?, " +
                     "DIAS_ALERTA_VENCIMIENTO = ?, NOMBRE_CORREDOR_AGENTE = ?, TELEFONO_CONTACTO_AGENTE = ?, " +
                     "DOCUMENTO_CARATULA_URL = ?, ESTADO = ? WHERE ID_POLIZA = ? AND ID_PROPIEDAD = ?";
        jdbcTemplate.update(sql, dto.getCompaniaAseguradora(), dto.getNumeroPoliza(), dto.getRamoCobertura(),
                dto.getValorAsegurado(), dto.getValorPrimaAnual(), dto.getFechaInicio(), dto.getFechaFin(),
                dto.getDiasAlertaVencimiento(), dto.getNombreCorredorAgente(), dto.getTelefonoContactoAgente(),
                dto.getDocumentoCaratulaUrl(), dto.getEstado(), dto.getIdPoliza(), dto.getIdPropiedad());
    }

    public void delete(Long idPoliza, Long idPropiedad) {
        String sql = "DELETE FROM POLIZAS_SEGURO WHERE ID_POLIZA = ? AND ID_PROPIEDAD = ?";
        jdbcTemplate.update(sql, idPoliza, idPropiedad);
    }
}
