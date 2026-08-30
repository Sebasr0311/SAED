package com.saed.backend.porteria.repository.impl;

import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.repository.PorteriaRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PorteriaRepositoryImpl implements PorteriaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ZoneId bogotaZone = ZoneId.of("America/Bogota");

    public PorteriaRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private ZonedDateTime toZDT(Timestamp ts) {
        if (ts == null) return null;
        return ts.toInstant().atZone(bogotaZone);
    }

    private Timestamp toTimestamp(ZonedDateTime zdt) {
        if (zdt == null) return null;
        return Timestamp.from(zdt.toInstant());
    }

    // --- VISITAS ---
    private final RowMapper<VisitaDTO> visitaMapper = (rs, rowNum) -> new VisitaDTO(
            rs.getLong("ID_VISITA"),
            rs.getLong("ID_UNIDAD"),
            rs.getLong("ID_VISITANTE"),
            rs.getString("METODO_INGRESO"),
            rs.getString("MOTIVO"),
            rs.getObject("AUTORIZADO_POR") != null ? rs.getLong("AUTORIZADO_POR") : null,
            toZDT(rs.getTimestamp("FECHA_PROGRAMADA")),
            rs.getString("ESTADO"),
            toZDT(rs.getTimestamp("FECHA_CREACION"))
    );

    @Override
    public VisitaDTO createVisita(VisitaRequestDTO request) {
        String sql = "INSERT INTO VISITAS (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, AUTORIZADO_POR, FECHA_PROGRAMADA, ESTADO) " +
                     "VALUES (:unidadId, :visitanteId, :metodoIngreso, :motivo, :autorizadoPor, :fechaProgramada, :estado)";
        
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unidadId", request.unidadId())
                .addValue("visitanteId", request.visitanteId())
                .addValue("metodoIngreso", request.metodoIngreso())
                .addValue("motivo", request.motivo())
                .addValue("autorizadoPor", request.autorizadoPor())
                .addValue("fechaProgramada", toTimestamp(request.fechaProgramada()))
                .addValue("estado", request.estado());
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_VISITA"});
        return getVisitaById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<VisitaDTO> getVisitaById(Long id) {
        String sql = "SELECT * FROM VISITAS WHERE ID_VISITA = :id";
        List<VisitaDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), visitaMapper);
        return list.stream().findFirst();
    }

    @Override
    public List<VisitaDTO> getVisitasByUnidad(Long unidadId) {
        String sql = "SELECT * FROM VISITAS WHERE ID_UNIDAD = :unidadId";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("unidadId", unidadId), visitaMapper);
    }

    @Override
    public VisitaDTO updateVisita(Long id, VisitaRequestDTO request) {
        String sql = "UPDATE VISITAS SET ID_UNIDAD = :unidadId, ID_VISITANTE = :visitanteId, " +
                     "METODO_INGRESO = :metodoIngreso, MOTIVO = :motivo, AUTORIZADO_POR = :autorizadoPor, " +
                     "FECHA_PROGRAMADA = :fechaProgramada, ESTADO = :estado WHERE ID_VISITA = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("unidadId", request.unidadId())
                .addValue("visitanteId", request.visitanteId())
                .addValue("metodoIngreso", request.metodoIngreso())
                .addValue("motivo", request.motivo())
                .addValue("autorizadoPor", request.autorizadoPor())
                .addValue("fechaProgramada", toTimestamp(request.fechaProgramada()))
                .addValue("estado", request.estado());
        jdbcTemplate.update(sql, params);
        return getVisitaById(id).orElseThrow();
    }

    @Override
    public void updateVisitaEstado(Long id, String estado) {
        String sql = "UPDATE VISITAS SET ESTADO = :estado WHERE ID_VISITA = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("estado", estado));
    }

    
    // --- HISTORIAL ---
    private final RowMapper<VisitaHistorialDTO> historialMapper = (rs, rowNum) -> new VisitaHistorialDTO(
            rs.getLong("ID_VISITA"),
            rs.getString("NOMBRE_VISITANTE"),
            rs.getString("APELLIDO_VISITANTE"),
            rs.getString("DOCUMENTO_VISITANTE"),
            rs.getString("NOMBRE_RESIDENTE"),
            rs.getString("NUMERO_APARTAMENTO"),
            toZDT(rs.getTimestamp("FECHA_VISITA")),
            toZDT(rs.getTimestamp("FECHA_SALIDA")),
            rs.getString("ESTADO"),
            rs.getString("TIPO_VEHICULO"),
            rs.getString("PLACA_VEHICULO"),
            rs.getString("CODIGO_PARQUEADERO")
    );

    @Override
    public List<VisitaHistorialDTO> getVisitasHistorial(String fechaInicio, String fechaFin) {
        String sql = "SELECT v.ID_VISITA, " +
                     "       pv.PRIMER_NOMBRE || ' ' || COALESCE(pv.SEGUNDO_NOMBRE, '') AS NOMBRE_VISITANTE, pv.PRIMER_APELLIDO || ' ' || COALESCE(pv.SEGUNDO_APELLIDO, '') AS APELLIDO_VISITANTE, pv.NUMERO_DOCUMENTO AS DOCUMENTO_VISITANTE, " +
                     "       pr.PRIMER_NOMBRE || ' ' || pr.PRIMER_APELLIDO AS NOMBRE_RESIDENTE, " +
                     "       u.IDENTIFICADOR AS NUMERO_APARTAMENTO, " +
                     "       (SELECT MIN(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'ENTRADA') AS FECHA_VISITA, " +
                     "       (SELECT MAX(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'SALIDA') AS FECHA_SALIDA, " +
                     "       v.ESTADO, " +
                     "       vv.TIPO_VEHICULO, vv.PLACA AS PLACA_VEHICULO, " +
                     "       pq.NUMERO_PARQUEADERO AS CODIGO_PARQUEADERO " +
                     "FROM VISITAS v " +
                     "JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN VISITANTES vis ON v.ID_VISITANTE = vis.ID_VISITANTE " +
                     "JOIN PERSONAS pv ON vis.ID_PERSONA = pv.ID_PERSONA " +
                     "LEFT JOIN UNIDAD_HABITANTES uh ON uh.ID_UNIDAD = v.ID_UNIDAD AND uh.ES_TITULAR = 1 " +
                     "LEFT JOIN PERSONAS pr ON uh.ID_PERSONA = pr.ID_PERSONA " +
                     "LEFT JOIN VEHICULOS_VISITA vv ON vv.ID_VISITA = v.ID_VISITA " +
                     "LEFT JOIN PARQUEADEROS pq ON pq.ID_PARQUEADERO = vv.ID_PARQUEADERO " +
                     "WHERE TRUNC(v.FECHA_CREACION) BETWEEN TO_DATE(:fechaInicio, 'YYYY-MM-DD') AND TO_DATE(:fechaFin, 'YYYY-MM-DD') " +
                     "ORDER BY v.FECHA_CREACION DESC";

        return jdbcTemplate.query(sql,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("fechaInicio", fechaInicio)
                        .addValue("fechaFin", fechaFin),
                historialMapper);
    }

    // --- DETALLE ---
    @Override
    public Optional<VisitaDetalleDTO> getVisitaDetalle(Long id) {
        String sql = "SELECT v.ID_VISITA, " +
                     "       pv.PRIMER_NOMBRE || ' ' || COALESCE(pv.SEGUNDO_NOMBRE, '') AS NOMBRE_VISITANTE, pv.PRIMER_APELLIDO || ' ' || COALESCE(pv.SEGUNDO_APELLIDO, '') AS APELLIDO_VISITANTE, " +
                     "       pv.NUMERO_DOCUMENTO AS DOCUMENTO_VISITANTE, pv.TELEFONO AS TELEFONO_VISITANTE, pv.EMAIL AS EMAIL_VISITANTE, " +
                     "       pr.PRIMER_NOMBRE || ' ' || pr.PRIMER_APELLIDO AS NOMBRE_RESIDENTE, " +
                     "       u.IDENTIFICADOR AS NUMERO_APARTAMENTO, NULL AS PISO, " +
                     "       (SELECT MIN(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'ENTRADA') AS FECHA_VISITA, " +
                     "       (SELECT MAX(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'SALIDA') AS FECHA_SALIDA, " +
                     "       v.ESTADO, v.MOTIVO AS NOTAS, " +
                     "       vv.TIPO_VEHICULO, vv.PLACA AS PLACA_VEHICULO, vv.DESCRIPCION_TIPO AS DESCRIPCION_VEHICULO, " +
                     "       pq.NUMERO_PARQUEADERO AS CODIGO_PARQUEADERO, " +
                     "       NULL AS FOTO_CAPTURA, " +
                     "       v.CANTIDAD_PERSONAS, " +
                     "       0 AS ES_FRECUENTE " +
                     "FROM VISITAS v " +
                     "JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN VISITANTES vis ON v.ID_VISITANTE = vis.ID_VISITANTE " +
                     "JOIN PERSONAS pv ON vis.ID_PERSONA = pv.ID_PERSONA " +
                     "LEFT JOIN UNIDAD_HABITANTES uh ON uh.ID_UNIDAD = v.ID_UNIDAD AND uh.ES_TITULAR = 1 " +
                     "LEFT JOIN PERSONAS pr ON uh.ID_PERSONA = pr.ID_PERSONA " +
                     "LEFT JOIN VEHICULOS_VISITA vv ON vv.ID_VISITA = v.ID_VISITA " +
                     "LEFT JOIN PARQUEADEROS pq ON pq.ID_PARQUEADERO = vv.ID_PARQUEADERO " +
                     "WHERE v.ID_VISITA = :id";

        List<VisitaDetalleDTO> list = jdbcTemplate.query(sql,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", id),
                (rs, rowNum) -> new VisitaDetalleDTO(
                        rs.getLong("ID_VISITA"),
                        rs.getString("NOMBRE_VISITANTE"),
                        rs.getString("APELLIDO_VISITANTE"),
                        rs.getString("DOCUMENTO_VISITANTE"),
                        rs.getString("TELEFONO_VISITANTE"),
                        rs.getString("EMAIL_VISITANTE"),
                        rs.getString("NOMBRE_RESIDENTE"),
                        rs.getString("NUMERO_APARTAMENTO"),
                        rs.getObject("PISO") != null ? rs.getInt("PISO") : null,
                        toZDT(rs.getTimestamp("FECHA_VISITA")),
                        toZDT(rs.getTimestamp("FECHA_SALIDA")),
                        rs.getString("ESTADO"),
                        rs.getString("NOTAS"),
                        rs.getString("TIPO_VEHICULO"),
                        rs.getString("PLACA_VEHICULO"),
                        rs.getString("DESCRIPCION_VEHICULO"),
                        rs.getString("CODIGO_PARQUEADERO"),
                        rs.getString("FOTO_CAPTURA"),
                        rs.getObject("CANTIDAD_PERSONAS") != null ? rs.getInt("CANTIDAD_PERSONAS") : null,
                        rs.getInt("ES_FRECUENTE") == 1
                ));
        return list.stream().findFirst();
    }

    @Override
    public List<VisitaListDTO> getVisitasResumen() {
        String sql = "SELECT v.ID_VISITA, p.PRIMER_NOMBRE || ' ' || COALESCE(p.SEGUNDO_NOMBRE, '') || ' ' || p.PRIMER_APELLIDO || ' ' || COALESCE(p.SEGUNDO_APELLIDO, '') AS nombreVisitante, p.NUMERO_DOCUMENTO AS documentoVisitante, u.IDENTIFICADOR AS numeroApartamento, " +
                     "(SELECT MIN(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'ENTRADA') AS fechaIngreso, " +
                     "(SELECT MAX(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'SALIDA') AS fechaSalida, " +
                     "v.ESTADO " +
                     "FROM VISITAS v " +
                     "JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN VISITANTES vis ON v.ID_VISITANTE = vis.ID_VISITANTE " +
                     "JOIN PERSONAS p ON vis.ID_PERSONA = p.ID_PERSONA " +
                     "WHERE 1=1 " +
                     "ORDER BY v.ID_VISITA DESC";
        
        return jdbcTemplate.query(sql,  (rs, rowNum) -> new VisitaListDTO(
                rs.getLong("ID_VISITA"),
                rs.getString("nombreVisitante"),
                rs.getString("documentoVisitante"),
                rs.getString("numeroApartamento"),
                toZDT(rs.getTimestamp("fechaIngreso")),
                toZDT(rs.getTimestamp("fechaSalida")),
                rs.getString("ESTADO")
        ));
    }

    // --- REGISTROS_ACCESO ---
    private final RowMapper<RegistroAccesoDTO> registroMapper = (rs, rowNum) -> new RegistroAccesoDTO(
            rs.getLong("ID_REGISTRO_ACCESO"),
            rs.getLong("ID_PROPIEDAD"),
            rs.getLong("ID_PORTERIA"),
            rs.getObject("ID_ACCESO_CONFIGURADO") != null ? rs.getLong("ID_ACCESO_CONFIGURADO") : null,
            rs.getObject("ID_VISITA") != null ? rs.getLong("ID_VISITA") : null,
            rs.getLong("ID_PERSONA"),
            rs.getObject("ID_UNIDAD") != null ? rs.getLong("ID_UNIDAD") : null,
            rs.getObject("ID_QR") != null ? rs.getLong("ID_QR") : null,
            rs.getString("TIPO_MOVIMIENTO"),
            rs.getString("METODO_AUTORIZACION"),
            toZDT(rs.getTimestamp("FECHA_HORA")),
            rs.getObject("PORTERO_OPERADOR") != null ? rs.getLong("PORTERO_OPERADOR") : null,
            rs.getString("PLACA_VEHICULO"),
            rs.getString("OBSERVACIONES")
    );

    @Override
    public RegistroAccesoDTO createRegistroAcceso(RegistroAccesoRequestDTO request) {
        String sql = "INSERT INTO REGISTROS_ACCESO (ID_PROPIEDAD, ID_PORTERIA, ID_ACCESO_CONFIGURADO, ID_VISITA, ID_PERSONA, ID_UNIDAD, ID_QR, TIPO_MOVIMIENTO, METODO_AUTORIZACION, PORTERO_OPERADOR, PLACA_VEHICULO, OBSERVACIONES) " +
                     "VALUES (:propiedadId, :porteriaId, :accesoId, :visitaId, :personaId, :unidadId, :qrId, :tipoMov, :metodoAuth, :porteroId, :placa, :obs)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propiedadId", request.propiedadId())
                .addValue("porteriaId", request.porteriaId())
                .addValue("accesoId", request.accesoConfiguradoId())
                .addValue("visitaId", request.visitaId())
                .addValue("personaId", request.personaId())
                .addValue("unidadId", request.unidadId())
                .addValue("qrId", request.qrId())
                .addValue("tipoMov", request.tipoMovimiento())
                .addValue("metodoAuth", request.metodoAutorizacion())
                .addValue("porteroId", request.porteroOperadorId())
                .addValue("placa", request.placaVehiculo())
                .addValue("obs", request.observaciones());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_REGISTRO_ACCESO"});
        return getRegistroAccesoById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<RegistroAccesoDTO> getRegistroAccesoById(Long id) {
        String sql = "SELECT * FROM REGISTROS_ACCESO WHERE ID_REGISTRO_ACCESO = :id";
        List<RegistroAccesoDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), registroMapper);
        return list.stream().findFirst();
    }

    @Override
    public List<RegistroAccesoDTO> getRegistrosByPropiedad(Long propiedadId) {
        String sql = "SELECT * FROM REGISTROS_ACCESO WHERE ID_PROPIEDAD = :propiedadId";
        return jdbcTemplate.query(sql,  registroMapper);
    }

    // --- QR_ACCESOS ---
    private final RowMapper<QrAccesoDTO> qrMapper = (rs, rowNum) -> new QrAccesoDTO(
            rs.getLong("ID_QR"),
            rs.getLong("ID_VISITA"),
            rs.getString("TOKEN_QR"),
            toZDT(rs.getTimestamp("FECHA_GENERACION")),
            toZDT(rs.getTimestamp("FECHA_EXPIRACION")),
            rs.getInt("USOS_PERMITIDOS"),
            rs.getInt("USOS_CONSUMIDOS"),
            toZDT(rs.getTimestamp("FECHA_REVOCACION")),
            rs.getString("MOTIVO_REVOCACION"),
            rs.getObject("REVOCADO_POR") != null ? rs.getLong("REVOCADO_POR") : null,
            rs.getString("ESTADO"),
            rs.getObject("GENERADO_POR") != null ? rs.getLong("GENERADO_POR") : null
    );

    @Override
    public QrAccesoDTO createQrAcceso(QrAccesoRequestDTO request) {
        String sql = "INSERT INTO QR_ACCESOS (ID_VISITA, TOKEN_QR, FECHA_EXPIRACION, USOS_PERMITIDOS, ESTADO, GENERADO_POR) " +
                     "VALUES (:visitaId, :token, :fechaExp, :usosPermitidos, :estado, :generadoPor)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("visitaId", request.visitaId())
                .addValue("token", request.tokenQr())
                .addValue("fechaExp", toTimestamp(request.fechaExpiracion()))
                .addValue("usosPermitidos", request.usosPermitidos())
                .addValue("estado", request.estado())
                .addValue("generadoPor", request.generadoPor());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_QR"});
        return getQrAccesoById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<QrAccesoDTO> getQrAccesoById(Long id) {
        String sql = "SELECT * FROM QR_ACCESOS WHERE ID_QR = :id";
        List<QrAccesoDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), qrMapper);
        return list.stream().findFirst();
    }

    @Override
    public Optional<QrAccesoDTO> getQrAccesoByToken(String token) {
        String sql = "SELECT * FROM QR_ACCESOS WHERE TOKEN_QR = :token";
        List<QrAccesoDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("token", token), qrMapper);
        return list.stream().findFirst();
    }

    @Override
    public void consumeQrUso(Long id) {
        String sql = "UPDATE QR_ACCESOS SET USOS_CONSUMIDOS = USOS_CONSUMIDOS + 1 WHERE ID_QR = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    // --- VEHICULOS_VISITA ---
    private final RowMapper<VehiculoVisitaDTO> vehiculoMapper = (rs, rowNum) -> new VehiculoVisitaDTO(
            rs.getLong("ID_VEHICULO_VISITA"),
            rs.getLong("ID_VISITA"),
            rs.getObject("ID_PARQUEADERO") != null ? rs.getLong("ID_PARQUEADERO") : null,
            rs.getString("PLACA"),
            rs.getString("TIPO_VEHICULO"),
            toZDT(rs.getTimestamp("FECHA_INGRESO")),
            toZDT(rs.getTimestamp("FECHA_SALIDA")),
            rs.getBigDecimal("COSTO_TOTAL"),
            rs.getString("ESTADO")
    );

    @Override
    public VehiculoVisitaDTO createVehiculoVisita(VehiculoVisitaRequestDTO request) {
        String sql = "INSERT INTO VEHICULOS_VISITA (ID_VISITA, ID_PARQUEADERO, PLACA, TIPO_VEHICULO, ESTADO) " +
                     "VALUES (:visitaId, :parqueaderoId, :placa, :tipoVehiculo, :estado)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("visitaId", request.visitaId())
                .addValue("parqueaderoId", request.parqueaderoId())
                .addValue("placa", request.placa())
                .addValue("tipoVehiculo", request.tipoVehiculo())
                .addValue("estado", request.estado());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_VEHICULO_VISITA"});
        return getVehiculoVisitaById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<VehiculoVisitaDTO> getVehiculoVisitaById(Long id) {
        String sql = "SELECT * FROM VEHICULOS_VISITA WHERE ID_VEHICULO_VISITA = :id";
        List<VehiculoVisitaDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), vehiculoMapper);
        return list.stream().findFirst();
    }

    @Override
    public void registerSalidaVehiculo(Long id, BigDecimal costoTotal) {
        String sql = "UPDATE VEHICULOS_VISITA SET FECHA_SALIDA = CURRENT_TIMESTAMP, COSTO_TOTAL = :costoTotal, ESTADO = 'AFUERA' WHERE ID_VEHICULO_VISITA = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("costoTotal", costoTotal));
    }
}




