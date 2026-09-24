package com.saed.backend.domicilios.repository.impl;

import com.saed.backend.domicilios.dto.DomicilioCreateDTO;
import com.saed.backend.domicilios.dto.DomicilioDTO;
import com.saed.backend.domicilios.repository.DomicilioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Repository
public class DomicilioRepositoryImpl implements DomicilioRepository {

    private static final Logger log = LoggerFactory.getLogger(DomicilioRepositoryImpl.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DomicilioRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private ZonedDateTime toZDT(Timestamp ts) {
        return ts != null ? ts.toInstant().atZone(ZoneId.of("America/Bogota")) : null;
    }

    private final RowMapper<DomicilioDTO> rowMapper = (rs, rowNum) -> new DomicilioDTO(
            rs.getLong("ID_DOMICILIO"),
            rs.getLong("ID_ORGANIZACION"),
            rs.getLong("ID_PROPIEDAD"),
            rs.getLong("ID_UNIDAD"),
            rs.getString("numeroUnidad"),
            rs.getObject("ID_PORTERIA") != null ? rs.getLong("ID_PORTERIA") : null,
            rs.getObject("ID_PERSONA_DESTINATARIO") != null ? rs.getLong("ID_PERSONA_DESTINATARIO") : null,
            rs.getString("nombreDestinatario"),
            rs.getString("EMPRESA"),
            rs.getString("NOMBRE_DOMICILIARIO"),
            rs.getString("DOCUMENTO_DOMICILIARIO"),
            rs.getString("TELEFONO_DOMICILIARIO"),
            rs.getString("TIPO_DOMICILIO"),
            rs.getString("NUMERO_GUIA"),
            rs.getString("MEDIO_TRANSPORTE"),
            rs.getString("PLACA_VEHICULO"),
            rs.getString("OBSERVACIONES"),
            rs.getString("ESTADO"),
            toZDT(rs.getTimestamp("FECHA_ENTRADA")),
            toZDT(rs.getTimestamp("FECHA_SALIDA")),
            rs.getLong("REGISTRADO_POR"),
            rs.getString("nombreRegistrador"),
            rs.getObject("FINALIZADO_POR") != null ? rs.getLong("FINALIZADO_POR") : null,
            rs.getString("nombreFinalizador"),
            toZDT(rs.getTimestamp("FECHA_CREACION"))
    );

    private String getBaseQuery() {
        return "SELECT d.*, " +
               "u.IDENTIFICADOR as \"numeroUnidad\", " +
               "(dest.PRIMER_NOMBRE || ' ' || dest.PRIMER_APELLIDO) as \"nombreDestinatario\", " +
               "(reg.PRIMER_NOMBRE || ' ' || reg.PRIMER_APELLIDO) as \"nombreRegistrador\", " +
               "(fin.PRIMER_NOMBRE || ' ' || fin.PRIMER_APELLIDO) as \"nombreFinalizador\" " +
               "FROM DOMICILIOS d " +
               "JOIN UNIDADES u ON d.ID_UNIDAD = u.ID_UNIDAD " +
               "LEFT JOIN PERSONAS dest ON d.ID_PERSONA_DESTINATARIO = dest.ID_PERSONA " +
               "LEFT JOIN USUARIOS u_reg ON d.REGISTRADO_POR = u_reg.ID_USUARIO " +
               "LEFT JOIN PERSONAS reg ON u_reg.ID_PERSONA = reg.ID_PERSONA " +
               "LEFT JOIN USUARIOS u_fin ON d.FINALIZADO_POR = u_fin.ID_USUARIO " +
               "LEFT JOIN PERSONAS fin ON u_fin.ID_PERSONA = fin.ID_PERSONA ";
    }

    @Override
    public DomicilioDTO registrarDomicilio(DomicilioCreateDTO dto, Long idOrganizacion, Long idPropiedad, Long registradoPor) {
        String sql = """
            INSERT INTO DOMICILIOS (
                ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ID_PORTERIA, ID_PERSONA_DESTINATARIO,
                EMPRESA, NOMBRE_DOMICILIARIO, DOCUMENTO_DOMICILIARIO, TELEFONO_DOMICILIARIO,
                TIPO_DOMICILIO, NUMERO_GUIA, MEDIO_TRANSPORTE, PLACA_VEHICULO, OBSERVACIONES,
                ESTADO, FECHA_ENTRADA, REGISTRADO_POR, FECHA_CREACION
            ) VALUES (
                :org, :prop, :unidad, :porteria, :destinatario,
                :empresa, :nombreDom, :docDom, :telDom,
                :tipoDom, :guia, :transporte, :placa, :obs,
                'EN_CURSO', FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota'), :registradoPor,
                FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota')
            )
        """;

        String tipoDom = (dto.tipoDomicilio() != null && !dto.tipoDomicilio().isBlank())
                ? dto.tipoDomicilio().trim().toUpperCase() : "COMIDA";
        String transporte = (dto.medioTransporte() != null && !dto.medioTransporte().isBlank())
                ? dto.medioTransporte().trim().toUpperCase() : "MOTO";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("org", idOrganizacion)
                .addValue("prop", idPropiedad)
                .addValue("unidad", dto.idUnidad())
                .addValue("porteria", dto.idPorteria())
                .addValue("destinatario", dto.idPersonaDestinatario())
                .addValue("empresa", dto.empresa().trim())
                .addValue("nombreDom", dto.nombreDomiciliario().trim())
                .addValue("docDom", dto.documentoDomiciliario() != null ? dto.documentoDomiciliario().trim() : null)
                .addValue("telDom", dto.telefonoDomiciliario() != null ? dto.telefonoDomiciliario().trim() : null)
                .addValue("tipoDom", tipoDom)
                .addValue("guia", dto.numeroGuia() != null ? dto.numeroGuia().trim() : null)
                .addValue("transporte", transporte)
                .addValue("placa", dto.placaVehiculo() != null ? dto.placaVehiculo().trim().toUpperCase() : null)
                .addValue("obs", dto.observaciones() != null ? dto.observaciones().trim() : null)
                .addValue("registradoPor", registradoPor);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_DOMICILIO"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Error al obtener ID generado para el domicilio");
        }
        return buscarPorId(key.longValue()).orElseThrow();
    }

    @Override
    public List<DomicilioDTO> listarDomicilios(Long idPropiedad, String estado, Long idUnidad) {
        StringBuilder sql = new StringBuilder(getBaseQuery());
        MapSqlParameterSource params = new MapSqlParameterSource();
        boolean firstClause = true;

        if (idPropiedad != null) {
            sql.append(firstClause ? " WHERE " : " AND ");
            sql.append("d.ID_PROPIEDAD = :propId");
            params.addValue("propId", idPropiedad);
            firstClause = false;
        }

        if (idUnidad != null) {
            sql.append(firstClause ? " WHERE " : " AND ");
            sql.append("d.ID_UNIDAD = :unitId");
            params.addValue("unitId", idUnidad);
            firstClause = false;
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(firstClause ? " WHERE " : " AND ");
            sql.append("d.ESTADO = :estado");
            params.addValue("estado", estado.trim().toUpperCase());
        }

        sql.append(" ORDER BY d.FECHA_ENTRADA DESC");
        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }

    @Override
    public Optional<DomicilioDTO> buscarPorId(Long idDomicilio) {
        String sql = getBaseQuery() + " WHERE d.ID_DOMICILIO = :id";
        List<DomicilioDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", idDomicilio), rowMapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public DomicilioDTO finalizarDomicilio(Long idDomicilio, Long finalizadoPor) {
        // Verificar existencia y estado actual
        List<String> estados = jdbcTemplate.query(
                "SELECT ESTADO FROM DOMICILIOS WHERE ID_DOMICILIO = :id",
                new MapSqlParameterSource("id", idDomicilio),
                (rs, rowNum) -> rs.getString("ESTADO")
        );

        if (estados.isEmpty()) {
            throw new NoSuchElementException("Domicilio no encontrado con ID: " + idDomicilio);
        }

        String estadoActual = estados.get(0);
        if (!"EN_CURSO".equalsIgnoreCase(estadoActual)) {
            throw new IllegalStateException("El domicilio ya se encuentra en estado: " + estadoActual);
        }

        String sql = """
            UPDATE DOMICILIOS
            SET ESTADO = 'FINALIZADO',
                FECHA_SALIDA = FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota'),
                FINALIZADO_POR = :finalizadoPor
            WHERE ID_DOMICILIO = :id AND ESTADO = 'EN_CURSO'
        """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", idDomicilio)
                .addValue("finalizadoPor", finalizadoPor));

        if (updated == 0) {
            throw new IllegalStateException("No fue posible finalizar el domicilio en estado actual");
        }

        return buscarPorId(idDomicilio).orElseThrow();
    }

    @Override
    public DomicilioDTO cancelarDomicilio(Long idDomicilio, Long canceladoPor) {
        List<String> estados = jdbcTemplate.query(
                "SELECT ESTADO FROM DOMICILIOS WHERE ID_DOMICILIO = :id",
                new MapSqlParameterSource("id", idDomicilio),
                (rs, rowNum) -> rs.getString("ESTADO")
        );

        if (estados.isEmpty()) {
            throw new NoSuchElementException("Domicilio no encontrado con ID: " + idDomicilio);
        }

        String estadoActual = estados.get(0);
        if (!"EN_CURSO".equalsIgnoreCase(estadoActual)) {
            throw new IllegalStateException("El domicilio ya se encuentra en estado: " + estadoActual);
        }

        String sql = """
            UPDATE DOMICILIOS
            SET ESTADO = 'CANCELADO',
                FECHA_SALIDA = FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota'),
                FINALIZADO_POR = :canceladoPor
            WHERE ID_DOMICILIO = :id AND ESTADO = 'EN_CURSO'
        """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", idDomicilio)
                .addValue("canceladoPor", canceladoPor));

        if (updated == 0) {
            throw new IllegalStateException("No fue posible cancelar el domicilio en estado actual");
        }

        return buscarPorId(idDomicilio).orElseThrow();
    }
}
