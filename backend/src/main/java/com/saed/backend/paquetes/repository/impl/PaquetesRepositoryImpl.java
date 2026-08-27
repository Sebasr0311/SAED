package com.saed.backend.paquetes.repository.impl;

import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.repository.PaquetesRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PaquetesRepositoryImpl implements PaquetesRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PaquetesRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private ZonedDateTime toZDT(Timestamp ts) {
        return ts != null ? ts.toInstant().atZone(ZoneId.of("America/Bogota")) : null;
    }

    private final RowMapper<PaqueteDTO> rowMapper = (rs, rowNum) -> new PaqueteDTO(
            rs.getLong("ID_PAQUETE"),
            rs.getLong("ID_PROPIEDAD"),
            rs.getLong("ID_PORTERIA"),
            rs.getLong("ID_UNIDAD"),
            rs.getString("numeroApartamento"),
            rs.getObject("ID_PERSONA_DESTINATARIO") != null ? rs.getLong("ID_PERSONA_DESTINATARIO") : null,
            rs.getString("nombreDestinatario"),
            rs.getString("EMPRESA_MENSAJERIA"),
            rs.getString("NUMERO_GUIA"),
            rs.getString("DESCRIPCION"),
            rs.getString("TAMANO"),
            rs.getString("FOTO_PAQUETE_URL"),
            rs.getString("CODIGO_RETIRO_PIN"),
            toZDT(rs.getTimestamp("FECHA_RECEPCION")),
            rs.getString("nombrePorteroRecibe"),
            toZDT(rs.getTimestamp("FECHA_NOTIFICACION")),
            toZDT(rs.getTimestamp("FECHA_ENTREGA")),
            rs.getString("nombrePersonaRecibe"),
            rs.getString("nombrePorteroEntrega"),
            rs.getString("FIRMA_URL"),
            rs.getString("ESTADO")
    );

    private String getBaseQuery() {
        return "SELECT p.*, " +
               "u.NUMERO as numeroApartamento, " +
               "(dest.NOMBRES || ' ' || dest.APELLIDOS) as nombreDestinatario, " +
               "(port_recibe.NOMBRES || ' ' || port_recibe.APELLIDOS) as nombrePorteroRecibe, " +
               "(port_entrega.NOMBRES || ' ' || port_entrega.APELLIDOS) as nombrePorteroEntrega, " +
               "(pers_recibe.NOMBRES || ' ' || pers_recibe.APELLIDOS) as nombrePersonaRecibe " +
               "FROM PAQUETES p " +
               "JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD " +
               "LEFT JOIN PERSONAS dest ON p.ID_PERSONA_DESTINATARIO = dest.ID_PERSONA " +
               "LEFT JOIN PERSONAS port_recibe ON p.RECIBIDO_POR_PORTERO = port_recibe.ID_PERSONA " +
               "LEFT JOIN PERSONAS port_entrega ON p.ENTREGADO_POR_PORTERO = port_entrega.ID_PERSONA " +
               "LEFT JOIN PERSONAS pers_recibe ON p.ENTREGADO_A_PERSONA = pers_recibe.ID_PERSONA ";
    }

    @Override
    public PaqueteDTO registrarPaquete(PaqueteRequestDTO request, Long idPropiedad, String codigoRetiro, Long idPorteroRegistra) {
        String sql = "INSERT INTO PAQUETES (ID_PROPIEDAD, ID_PORTERIA, ID_UNIDAD, ID_PERSONA_DESTINATARIO, EMPRESA_MENSAJERIA, NUMERO_GUIA, DESCRIPCION, TAMANO, FOTO_PAQUETE_URL, CODIGO_RETIRO_PIN, RECIBIDO_POR_PORTERO, ESTADO) " +
                     "VALUES (:propiedad, :porteria, :unidad, :destinatario, :empresa, :guia, :descripcion, :tamano, :foto, :pin, (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :portero), 'RECIBIDO')";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propiedad", idPropiedad)
                .addValue("porteria", request.idPorteria())
                .addValue("unidad", request.idUnidad())
                .addValue("destinatario", request.idPersonaDestinatario())
                .addValue("empresa", request.empresaMensajeria())
                .addValue("guia", request.numeroGuia())
                .addValue("descripcion", request.descripcion())
                .addValue("tamano", request.tamano())
                .addValue("foto", request.fotoPaqueteUrl())
                .addValue("pin", codigoRetiro)
                .addValue("portero", idPorteroRegistra);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PAQUETE"});
        return getPaqueteById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public List<PaqueteDTO> getPaquetesList() {
        return jdbcTemplate.query(getBaseQuery() + " ORDER BY p.FECHA_RECEPCION DESC", rowMapper);
    }

    @Override
    public Optional<PaqueteDTO> getPaqueteById(Long idPaquete) {
        String sql = getBaseQuery() + " WHERE p.ID_PAQUETE = :id";
        List<PaqueteDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", idPaquete), rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public PaqueteDTO actualizarPaquete(Long idPaquete, PaqueteRequestDTO request) {
        String sql = "UPDATE PAQUETES SET ID_UNIDAD = :unidad, ID_PERSONA_DESTINATARIO = :destinatario, EMPRESA_MENSAJERIA = :empresa, NUMERO_GUIA = :guia, DESCRIPCION = :descripcion, TAMANO = :tamano, FOTO_PAQUETE_URL = :foto WHERE ID_PAQUETE = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", idPaquete)
                .addValue("unidad", request.idUnidad())
                .addValue("destinatario", request.idPersonaDestinatario())
                .addValue("empresa", request.empresaMensajeria())
                .addValue("guia", request.numeroGuia())
                .addValue("descripcion", request.descripcion())
                .addValue("tamano", request.tamano())
                .addValue("foto", request.fotoPaqueteUrl());
        jdbcTemplate.update(sql, params);
        return getPaqueteById(idPaquete).orElseThrow();
    }

    @Override
    public void registrarEntrega(Long idPaquete, PaqueteEntregaDTO entregaDTO, Long idPorteroEntrega) {
        String sql = "UPDATE PAQUETES SET ESTADO = 'ENTREGADO', FECHA_ENTREGA = CURRENT_TIMESTAMP, ENTREGADO_A_PERSONA = :persona, ENTREGADO_POR_PORTERO = (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :portero), FIRMA_URL = :firma WHERE ID_PAQUETE = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", idPaquete)
                .addValue("persona", entregaDTO.idPersonaRecibe())
                .addValue("portero", idPorteroEntrega)
                .addValue("firma", entregaDTO.firmaUrl());
        jdbcTemplate.update(sql, params);
    }
}


