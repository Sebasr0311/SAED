package com.saed.backend.porteria.repository.impl;

import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.repository.PorteriaRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PorteriaRepositoryImpl implements PorteriaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SimpleJdbcCall validarConsumirQrCall;
    private final ZoneId bogotaZone = ZoneId.of("America/Bogota");

    public PorteriaRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.validarConsumirQrCall = new SimpleJdbcCall(jdbcTemplate.getJdbcTemplate())
                .withProcedureName("SP_VALIDAR_CONSUMIR_QR")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_token_qr", Types.VARCHAR),
                        new SqlParameter("p_id_porteria", Types.NUMERIC),
                        new SqlParameter("p_portero_usuario", Types.NUMERIC),
                        new SqlOutParameter("p_valido", Types.CHAR),
                        new SqlOutParameter("p_mensaje", Types.VARCHAR),
                        new SqlOutParameter("p_id_visita", Types.NUMERIC)
                );
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
    public List<VisitaHistorialDTO> getVisitasHistorial(Long propId, String fechaInicio, String fechaFin) {
        String sql = "SELECT v.ID_VISITA, " +
                     "       pv.PRIMER_NOMBRE || ' ' || COALESCE(pv.SEGUNDO_NOMBRE, '') AS NOMBRE_VISITANTE, pv.PRIMER_APELLIDO || ' ' || COALESCE(pv.SEGUNDO_APELLIDO, '') AS APELLIDO_VISITANTE, pv.NUMERO_DOCUMENTO AS DOCUMENTO_VISITANTE, " +
                     "       COALESCE(TRIM(p_aut.PRIMER_NOMBRE || ' ' || COALESCE(p_aut.PRIMER_APELLIDO, '')), TRIM(pr.PRIMER_NOMBRE || ' ' || COALESCE(pr.PRIMER_APELLIDO, ''))) AS NOMBRE_RESIDENTE, " +
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
                     "LEFT JOIN USUARIOS u_aut ON v.AUTORIZADO_POR = u_aut.ID_USUARIO " +
                     "LEFT JOIN PERSONAS p_aut ON u_aut.ID_PERSONA = p_aut.ID_PERSONA " +
                     "LEFT JOIN RESIDENTES_UNIDAD uh ON uh.ID_UNIDAD = v.ID_UNIDAD AND uh.TIPO_RESIDENTE IN ('PROPIETARIO', 'ARRENDATARIO', 'TITULAR') " +
                     "LEFT JOIN PERSONAS pr ON uh.ID_PERSONA = pr.ID_PERSONA " +
                     "LEFT JOIN VEHICULOS_VISITA vv ON vv.ID_VISITA = v.ID_VISITA " +
                     "LEFT JOIN PARQUEADEROS pq ON pq.ID_PARQUEADERO = vv.ID_PARQUEADERO " +
                     "WHERE u.ID_PROPIEDAD = :propId " +
                     "AND TRUNC(v.FECHA_CREACION) BETWEEN TO_DATE(:fechaInicio, 'YYYY-MM-DD') AND TO_DATE(:fechaFin, 'YYYY-MM-DD') " +
                     "ORDER BY v.FECHA_CREACION DESC";

        return jdbcTemplate.query(sql,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("propId", propId)
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
                     "       COALESCE(TRIM(p_aut.PRIMER_NOMBRE || ' ' || COALESCE(p_aut.PRIMER_APELLIDO, '')), TRIM(pr.PRIMER_NOMBRE || ' ' || COALESCE(pr.PRIMER_APELLIDO, ''))) AS NOMBRE_RESIDENTE, " +
                     "       u.IDENTIFICADOR AS NUMERO_APARTAMENTO, NULL AS PISO, " +
                     "       (SELECT MIN(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'ENTRADA') AS FECHA_VISITA, " +
                     "       (SELECT MAX(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'SALIDA') AS FECHA_SALIDA, " +
                     "       v.ESTADO, v.MOTIVO AS NOTAS, " +
                     "       vv.TIPO_VEHICULO, vv.PLACA AS PLACA_VEHICULO, NULL AS DESCRIPCION_VEHICULO, " +
                     "       pq.NUMERO_PARQUEADERO AS CODIGO_PARQUEADERO, " +
                     "       NULL AS FOTO_CAPTURA, " +
                     "       1 AS CANTIDAD_PERSONAS, " +
                     "       0 AS ES_FRECUENTE " +
                     "FROM VISITAS v " +
                     "JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN VISITANTES vis ON v.ID_VISITANTE = vis.ID_VISITANTE " +
                     "JOIN PERSONAS pv ON vis.ID_PERSONA = pv.ID_PERSONA " +
                     "LEFT JOIN USUARIOS u_aut ON v.AUTORIZADO_POR = u_aut.ID_USUARIO " +
                     "LEFT JOIN PERSONAS p_aut ON u_aut.ID_PERSONA = p_aut.ID_PERSONA " +
                     "LEFT JOIN RESIDENTES_UNIDAD uh ON uh.ID_UNIDAD = v.ID_UNIDAD AND uh.TIPO_RESIDENTE IN ('PROPIETARIO', 'ARRENDATARIO', 'TITULAR') " +
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
    public List<VisitaListDTO> getVisitasResumen(Long propId) {
        String sql = "SELECT v.ID_VISITA, p.PRIMER_NOMBRE || ' ' || COALESCE(p.SEGUNDO_NOMBRE, '') || ' ' || p.PRIMER_APELLIDO || ' ' || COALESCE(p.SEGUNDO_APELLIDO, '') AS nombreVisitante, p.NUMERO_DOCUMENTO AS documentoVisitante, u.IDENTIFICADOR AS numeroApartamento, " +
                     "(SELECT MIN(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'ENTRADA') AS fechaIngreso, " +
                     "(SELECT MAX(FECHA_HORA) FROM REGISTROS_ACCESO ra WHERE ra.ID_VISITA = v.ID_VISITA AND ra.TIPO_MOVIMIENTO = 'SALIDA') AS fechaSalida, " +
                     "v.ESTADO " +
                     "FROM VISITAS v " +
                     "JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN VISITANTES vis ON v.ID_VISITANTE = vis.ID_VISITANTE " +
                     "JOIN PERSONAS p ON vis.ID_PERSONA = p.ID_PERSONA " +
                     "WHERE u.ID_PROPIEDAD = :propId " +
                     "ORDER BY v.ID_VISITA DESC";
        
        return jdbcTemplate.query(sql, new MapSqlParameterSource("propId", propId), (rs, rowNum) -> new VisitaListDTO(
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
        return jdbcTemplate.query(sql, new MapSqlParameterSource("propiedadId", propiedadId), registroMapper);
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

    @Override
    public QrConsumoResultadoDTO validarYConsumirQrSp(String tokenQr, Long idPorteria, Long idUsuarioPortero) {
        MapSqlParameterSource in = new MapSqlParameterSource()
                .addValue("p_token_qr", tokenQr)
                .addValue("p_id_porteria", idPorteria)
                .addValue("p_portero_usuario", idUsuarioPortero);

        Map<String, Object> out = validarConsumirQrCall.execute(in);

        Object validoObj = out.get("p_valido") != null ? out.get("p_valido") : out.get("P_VALIDO");
        boolean valido = validoObj != null && "S".equalsIgnoreCase(validoObj.toString().trim());

        Object msgObj = out.get("p_mensaje") != null ? out.get("p_mensaje") : out.get("P_MENSAJE");
        String mensaje = msgObj != null ? msgObj.toString() : "";

        Object visitaIdObj = out.get("p_id_visita") != null ? out.get("p_id_visita") : out.get("P_ID_VISITA");
        Long visitaId = visitaIdObj instanceof Number num ? num.longValue() : null;

        return new QrConsumoResultadoDTO(valido, mensaje, visitaId);
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
        String sql = "UPDATE VEHICULOS_VISITA SET FECHA_SALIDA = CURRENT_TIMESTAMP, COSTO_TOTAL = :costoTotal, ESTADO = 'SALIO' WHERE ID_VEHICULO_VISITA = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("costoTotal", costoTotal));
    }

    // --- LIFECYCLE (F07-01 / F07-03) ---
    @Override
    public void complementEntrada(Long visitaId, String placa, String observaciones) {
        jdbcTemplate.update(
                "UPDATE REGISTROS_ACCESO SET PLACA_VEHICULO = :placa, OBSERVACIONES = :obs "
                        + "WHERE ID_REGISTRO_ACCESO = (SELECT MAX(ID_REGISTRO_ACCESO) FROM REGISTROS_ACCESO "
                        + "WHERE ID_VISITA = :visitaId AND TIPO_MOVIMIENTO = 'ENTRADA')",
                new MapSqlParameterSource("placa", placa).addValue("obs", observaciones).addValue("visitaId", visitaId));
    }

    @Override
    public List<Long> findActiveUnitIdsForUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :u AND ESTADO IN ('ACTIVA', 'ACTIVO') "
                        + "AND ID_UNIDAD IS NOT NULL",
                new MapSqlParameterSource("u", userId), (rs, r) -> rs.getLong("ID_UNIDAD"));
    }

    @Override
    public Optional<Long> findVisitaPropertyId(Long visitaId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT u.ID_PROPIEDAD FROM VISITAS v JOIN UNIDADES u ON u.ID_UNIDAD = v.ID_UNIDAD WHERE v.ID_VISITA = :id",
                new MapSqlParameterSource("id", visitaId), (rs, r) -> rs.getLong("ID_PROPIEDAD"));
        return ids.stream().findFirst();
    }

    @Override
    public Optional<Long> findFirstPorteriaId(Long propId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT ID_PORTERIA FROM PORTERIAS WHERE ID_PROPIEDAD = :propId ORDER BY ID_PORTERIA FETCH FIRST 1 ROWS ONLY",
                new MapSqlParameterSource("propId", propId), (rs, r) -> rs.getLong("ID_PORTERIA"));
        return ids.stream().findFirst();
    }

    @Override
    public int transitionVisitaEstado(Long visitaId, String nuevoEstado, List<String> desde) {
        return jdbcTemplate.update(
                "UPDATE VISITAS SET ESTADO = :estado WHERE ID_VISITA = :id AND ESTADO IN (:desde)",
                new MapSqlParameterSource("id", visitaId).addValue("estado", nuevoEstado).addValue("desde", desde));
    }

    @Override
    public int revokeActiveQrs(Long visitaId, Long revocadoPor, String motivo) {
        return jdbcTemplate.update(
                "UPDATE QR_ACCESOS SET ESTADO = 'REVOCADO', FECHA_REVOCACION = CURRENT_TIMESTAMP, "
                        + "MOTIVO_REVOCACION = :motivo, REVOCADO_POR = :por WHERE ID_VISITA = :id AND ESTADO = 'ACTIVO'",
                new MapSqlParameterSource("id", visitaId).addValue("motivo", motivo).addValue("por", revocadoPor));
    }

    @Override
    public int revokeQr(Long idQr, Long revocadoPor, String motivo) {
        return jdbcTemplate.update(
                "UPDATE QR_ACCESOS SET ESTADO = 'REVOCADO', FECHA_REVOCACION = CURRENT_TIMESTAMP, "
                        + "MOTIVO_REVOCACION = :motivo, REVOCADO_POR = :por WHERE ID_QR = :id AND ESTADO = 'ACTIVO'",
                new MapSqlParameterSource("id", idQr).addValue("motivo", motivo).addValue("por", revocadoPor));
    }

    @Override
    public Optional<ParkingSpotClaim> claimAvailableSpot(Long propId, String tipoParqueadero) {
        MapSqlParameterSource params = new MapSqlParameterSource("propId", propId).addValue("tipo", tipoParqueadero);
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                "SELECT ID_PARQUEADERO, NUMERO_PARQUEADERO FROM PARQUEADEROS "
                        + "WHERE ID_PROPIEDAD = :propId AND TIPO = :tipo AND ESTADO = 'DISPONIBLE' "
                        + "ORDER BY ID_PARQUEADERO FETCH FIRST 20 ROWS ONLY",
                params);
        for (Map<String, Object> c : candidates) {
            Long id = ((Number) c.get("ID_PARQUEADERO")).longValue();
            // Conditional update: two concurrent claims of the same spot cannot both succeed.
            int claimed = jdbcTemplate.update(
                    "UPDATE PARQUEADEROS SET ESTADO = 'ASIGNADO' WHERE ID_PARQUEADERO = :id "
                            + "AND ID_PROPIEDAD = :propId AND TIPO = :tipo AND ESTADO = 'DISPONIBLE'",
                    new MapSqlParameterSource("id", id).addValue("propId", propId).addValue("tipo", tipoParqueadero));
            if (claimed == 1) {
                Object numero = c.get("NUMERO_PARQUEADERO");
                return Optional.of(new ParkingSpotClaim(id, numero != null ? numero.toString() : null));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<VehiculoVisitaDTO> findVehiculosDentro(Long visitaId) {
        return jdbcTemplate.query(
                "SELECT * FROM VEHICULOS_VISITA WHERE ID_VISITA = :id AND ESTADO = 'DENTRO'",
                new MapSqlParameterSource("id", visitaId), vehiculoMapper);
    }

    @Override
    public boolean closeVehiculoVisita(Long id, BigDecimal costoTotal) {
        return jdbcTemplate.update(
                "UPDATE VEHICULOS_VISITA SET FECHA_SALIDA = CURRENT_TIMESTAMP, COSTO_TOTAL = :costo, ESTADO = 'SALIO' "
                        + "WHERE ID_VEHICULO_VISITA = :id AND ESTADO = 'DENTRO'",
                new MapSqlParameterSource("id", id).addValue("costo", costoTotal)) == 1;
    }

    @Override
    public void releaseParqueadero(Long parqueaderoId) {
        jdbcTemplate.update(
                "UPDATE PARQUEADEROS SET ESTADO = 'DISPONIBLE' WHERE ID_PARQUEADERO = :id AND ESTADO = 'ASIGNADO' "
                        + "AND NOT EXISTS (SELECT 1 FROM VEHICULOS_VISITA vv WHERE vv.ID_PARQUEADERO = :id AND vv.ESTADO = 'DENTRO')",
                new MapSqlParameterSource("id", parqueaderoId));
    }

    @Override
    public void insertSalidaVisita(Long visitaId, Long porteriaId, Long porteroId) {
        jdbcTemplate.update(
                "INSERT INTO REGISTROS_ACCESO (ID_PROPIEDAD, ID_PORTERIA, ID_VISITA, ID_PERSONA, ID_UNIDAD, "
                        + "TIPO_MOVIMIENTO, METODO_AUTORIZACION, PORTERO_OPERADOR) "
                        + "SELECT u.ID_PROPIEDAD, :porteria, v.ID_VISITA, vis.ID_PERSONA, v.ID_UNIDAD, 'SALIDA', 'MANUAL_PORTERO', :portero "
                        + "FROM VISITAS v JOIN UNIDADES u ON u.ID_UNIDAD = v.ID_UNIDAD "
                        + "JOIN VISITANTES vis ON vis.ID_VISITANTE = v.ID_VISITANTE WHERE v.ID_VISITA = :id",
                new MapSqlParameterSource("id", visitaId).addValue("porteria", porteriaId).addValue("portero", porteroId));
    }
}




