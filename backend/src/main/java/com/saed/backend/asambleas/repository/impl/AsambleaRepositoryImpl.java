package com.saed.backend.asambleas.repository.impl;

import com.saed.backend.asambleas.dto.*;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AsambleaRepositoryImpl implements AsambleaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    public AsambleaRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AsambleaDTO> asambleaRowMapper = (rs, rowNum) -> {
        AsambleaDTO dto = new AsambleaDTO();
        dto.setIdAsamblea(rs.getLong("ID_ASAMBLEA"));
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        dto.setTipo(rs.getString("TIPO"));
        dto.setModalidad(rs.getString("MODALIDAD"));
        dto.setTitulo(rs.getString("TITULO"));
        dto.setConvocatoriaNumero(rs.getInt("CONVOCATORIA_NUMERO"));

        if (rs.getTimestamp("FECHA_HORA_PRIMERA_CONV") != null) {
            dto.setFechaHoraPrimeraConv(rs.getTimestamp("FECHA_HORA_PRIMERA_CONV").toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime());
        }
        if (rs.getTimestamp("FECHA_HORA_SEGUNDA_CONV") != null) {
            dto.setFechaHoraSegundaConv(rs.getTimestamp("FECHA_HORA_SEGUNDA_CONV").toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime());
        }

        dto.setLugarOEnlace(rs.getString("LUGAR_O_ENLACE"));
        dto.setOrdenDelDia(rs.getString("ORDEN_DEL_DIA"));
        dto.setQuorumRequeridoPct(rs.getBigDecimal("QUORUM_REQUERIDO_PCT"));
        dto.setQuorumAlcanzadoPct(rs.getBigDecimal("QUORUM_ALCANZADO_PCT"));
        dto.setEstado(rs.getString("ESTADO"));

        long convocadaPor = rs.getLong("CONVOCADA_POR");
        if (!rs.wasNull()) dto.setConvocadaPor(convocadaPor);

        if (rs.getTimestamp("FECHA_CREACION") != null) {
            dto.setFechaCreacion(rs.getTimestamp("FECHA_CREACION").toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime());
        }

        dto.setTotalAsistentes(rs.getInt("TOTAL_ASISTENTES"));
        dto.setTotalVotaciones(rs.getInt("TOTAL_VOTACIONES"));

        return dto;
    };

    private final RowMapper<AsistenciaDTO> asistenciaRowMapper = (rs, rowNum) -> {
        AsistenciaDTO dto = new AsistenciaDTO();
        dto.setIdAsistencia(rs.getLong("ID_ASISTENCIA"));
        dto.setIdAsamblea(rs.getLong("ID_ASAMBLEA"));
        dto.setIdUnidad(rs.getLong("ID_UNIDAD"));
        dto.setUnidadIdentificador(rs.getString("UNIDAD_IDENTIFICADOR"));
        dto.setIdPersonaAsistente(rs.getLong("ID_PERSONA_ASISTENTE"));
        dto.setNombreAsistente(rs.getString("NOMBRE_ASISTENTE"));
        dto.setDocumentoAsistente(rs.getString("DOCUMENTO_ASISTENTE"));
        dto.setEsPropietarioDirecto(rs.getString("ES_PROPIETARIO_DIRECTO"));

        long idPoder = rs.getLong("ID_PODER");
        if (!rs.wasNull()) dto.setIdPoder(idPoder);

        dto.setCoeficientePonderado(rs.getBigDecimal("COEFICIENTE_PONDERADO"));

        if (rs.getTimestamp("HORA_REGISTRO") != null) {
            dto.setHoraRegistro(rs.getTimestamp("HORA_REGISTRO").toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime());
        }
        if (rs.getTimestamp("HORA_RETIRO") != null) {
            dto.setHoraRetiro(rs.getTimestamp("HORA_RETIRO").toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime());
        }

        return dto;
    };

    private final RowMapper<PoderDTO> poderRowMapper = (rs, rowNum) -> {
        PoderDTO dto = new PoderDTO();
        dto.setIdPoder(rs.getLong("ID_PODER"));
        dto.setIdAsamblea(rs.getLong("ID_ASAMBLEA"));
        dto.setIdUnidad(rs.getLong("ID_UNIDAD"));
        dto.setUnidadIdentificador(rs.getString("UNIDAD_IDENTIFICADOR"));
        dto.setIdPersonaPropietario(rs.getLong("ID_PERSONA_PROPIETARIO"));
        dto.setNombrePropietario(rs.getString("NOMBRE_PROPIETARIO"));
        dto.setDocumentoPropietario(rs.getString("DOCUMENTO_PROPIETARIO"));
        dto.setIdPersonaApoderado(rs.getLong("ID_PERSONA_APODERADO"));
        dto.setNombreApoderado(rs.getString("NOMBRE_APODERADO"));
        dto.setDocumentoApoderado(rs.getString("DOCUMENTO_APODERADO"));
        dto.setDocumentoPoderUrl(rs.getString("DOCUMENTO_PODER_URL"));
        dto.setEstado(rs.getString("ESTADO"));

        long validador = rs.getLong("VALIDADO_POR");
        if (!rs.wasNull()) dto.setValidadoPor(validador);

        if (rs.getTimestamp("FECHA_REGISTRO") != null) {
            dto.setFechaRegistro(rs.getTimestamp("FECHA_REGISTRO").toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime());
        }

        return dto;
    };

    private final RowMapper<VotacionDTO> votacionRowMapper = (rs, rowNum) -> {
        VotacionDTO dto = new VotacionDTO();
        dto.setIdVotacion(rs.getLong("ID_VOTACION"));
        dto.setIdAsamblea(rs.getLong("ID_ASAMBLEA"));
        dto.setPuntoOrdenDia(rs.getInt("PUNTO_ORDEN_DIA"));
        dto.setTitulo(rs.getString("TITULO"));
        dto.setDescripcion(rs.getString("DESCRIPCION"));
        dto.setTipoMayoriaRequerida(rs.getString("TIPO_MAYORIA_REQUERIDA"));

        if (rs.getTimestamp("HORA_APERTURA") != null) {
            dto.setHoraApertura(rs.getTimestamp("HORA_APERTURA").toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime());
        }
        if (rs.getTimestamp("HORA_CIERRE") != null) {
            dto.setHoraCierre(rs.getTimestamp("HORA_CIERRE").toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime());
        }

        dto.setEstado(rs.getString("ESTADO"));
        dto.setVotosSi(rs.getBigDecimal("VOTOS_SI"));
        dto.setVotosNo(rs.getBigDecimal("VOTOS_NO"));
        dto.setVotosBlanco(rs.getBigDecimal("VOTOS_BLANCO"));
        dto.setVotosAbstencion(rs.getBigDecimal("VOTOS_ABSTENCION"));
        dto.setTotalVotos(rs.getInt("TOTAL_VOTOS"));

        // Evaluar aprobación preliminar según mayoría
        BigDecimal si = dto.getVotosSi() != null ? dto.getVotosSi() : BigDecimal.ZERO;
        BigDecimal no = dto.getVotosNo() != null ? dto.getVotosNo() : BigDecimal.ZERO;
        BigDecimal sumaTotal = si.add(no)
                .add(dto.getVotosBlanco() != null ? dto.getVotosBlanco() : BigDecimal.ZERO)
                .add(dto.getVotosAbstencion() != null ? dto.getVotosAbstencion() : BigDecimal.ZERO);

        boolean aprobada = false;
        if (sumaTotal.compareTo(BigDecimal.ZERO) > 0) {
            if ("CALIFICADA_70_PCT".equals(dto.getTipoMayoriaRequerida())) {
                aprobada = si.divide(sumaTotal, 4, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(0.70)) >= 0;
            } else if ("UNANIMIDAD_100_PCT".equals(dto.getTipoMayoriaRequerida())) {
                aprobada = si.compareTo(sumaTotal) == 0;
            } else {
                // SIMPLE_50_MAS_1
                aprobada = si.compareTo(no) > 0 && si.divide(sumaTotal, 4, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(0.50)) > 0;
            }
        }
        dto.setAprobada(aprobada);

        return dto;
    };

    @Override
    public List<AsambleaDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = "SELECT a.ID_ASAMBLEA, a.ID_PROPIEDAD, a.TIPO, a.MODALIDAD, a.TITULO, a.CONVOCATORIA_NUMERO, " +
                "a.FECHA_HORA_PRIMERA_CONV, a.FECHA_HORA_SEGUNDA_CONV, a.LUGAR_O_ENLACE, a.ORDEN_DEL_DIA, " +
                "a.QUORUM_REQUERIDO_PCT, a.QUORUM_ALCANZADO_PCT, a.ESTADO, a.CONVOCADA_POR, a.FECHA_CREACION, " +
                "(SELECT COUNT(1) FROM ASISTENCIAS_ASAMBLEA asi WHERE asi.ID_ASAMBLEA = a.ID_ASAMBLEA AND asi.HORA_RETIRO IS NULL) as TOTAL_ASISTENTES, " +
                "(SELECT COUNT(1) FROM VOTACIONES v WHERE v.ID_ASAMBLEA = a.ID_ASAMBLEA) as TOTAL_VOTACIONES " +
                "FROM ASAMBLEAS a " +
                "WHERE a.ID_PROPIEDAD = :idPropiedad " +
                "ORDER BY a.FECHA_HORA_PRIMERA_CONV DESC";

        return jdbcTemplate.query(sql, new MapSqlParameterSource("idPropiedad", idPropiedad), asambleaRowMapper);
    }

    @Override
    public Optional<AsambleaDTO> findById(Long idAsamblea) {
        String sql = "SELECT a.ID_ASAMBLEA, a.ID_PROPIEDAD, a.TIPO, a.MODALIDAD, a.TITULO, a.CONVOCATORIA_NUMERO, " +
                "a.FECHA_HORA_PRIMERA_CONV, a.FECHA_HORA_SEGUNDA_CONV, a.LUGAR_O_ENLACE, a.ORDEN_DEL_DIA, " +
                "a.QUORUM_REQUERIDO_PCT, a.QUORUM_ALCANZADO_PCT, a.ESTADO, a.CONVOCADA_POR, a.FECHA_CREACION, " +
                "(SELECT COUNT(1) FROM ASISTENCIAS_ASAMBLEA asi WHERE asi.ID_ASAMBLEA = a.ID_ASAMBLEA AND asi.HORA_RETIRO IS NULL) as TOTAL_ASISTENTES, " +
                "(SELECT COUNT(1) FROM VOTACIONES v WHERE v.ID_ASAMBLEA = a.ID_ASAMBLEA) as TOTAL_VOTACIONES " +
                "FROM ASAMBLEAS a " +
                "WHERE a.ID_ASAMBLEA = :idAsamblea";

        try {
            AsambleaDTO asamblea = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idAsamblea", idAsamblea), asambleaRowMapper);
            return Optional.ofNullable(asamblea);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Long createAsamblea(AsambleaCreateRequestDTO request, Long idPropiedad, Long idUsuario) {
        String sql = "INSERT INTO ASAMBLEAS ( " +
                "ID_PROPIEDAD, TIPO, MODALIDAD, TITULO, CONVOCATORIA_NUMERO, " +
                "FECHA_HORA_PRIMERA_CONV, FECHA_HORA_SEGUNDA_CONV, LUGAR_O_ENLACE, " +
                "ORDEN_DEL_DIA, QUORUM_REQUERIDO_PCT, QUORUM_ALCANZADO_PCT, " +
                "ESTADO, CONVOCADA_POR, FECHA_CREACION " +
                ") VALUES ( " +
                ":idPropiedad, :tipo, :modalidad, :titulo, :convocatoriaNumero, " +
                "FROM_TZ(CAST(TO_TIMESTAMP(SUBSTR(REPLACE(:fechaHoraPrimeraConv, 'T', ' '), 1, 19), 'YYYY-MM-DD HH24:MI:SS') AS TIMESTAMP), 'America/Bogota'), " +
                "CASE WHEN :fechaHoraSegundaConv IS NOT NULL THEN FROM_TZ(CAST(TO_TIMESTAMP(SUBSTR(REPLACE(:fechaHoraSegundaConv, 'T', ' '), 1, 19), 'YYYY-MM-DD HH24:MI:SS') AS TIMESTAMP), 'America/Bogota') ELSE NULL END, " +
                ":lugarOEnlace, :ordenDelDia, :quorumRequeridoPct, 0, " +
                "'CONVOCADA', :convocadaPor, FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') " +
                ")";

        BigDecimal quorumReq = request.getQuorumRequeridoPct() != null ? request.getQuorumRequeridoPct() : BigDecimal.valueOf(50.01);
        int convNum = request.getConvocatoriaNumero() != null ? request.getConvocatoriaNumero() : 1;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", idPropiedad)
                .addValue("tipo", request.getTipo())
                .addValue("modalidad", request.getModalidad())
                .addValue("titulo", request.getTitulo())
                .addValue("convocatoriaNumero", convNum)
                .addValue("fechaHoraPrimeraConv", request.getFechaHoraPrimeraConv())
                .addValue("fechaHoraSegundaConv", request.getFechaHoraSegundaConv())
                .addValue("lugarOEnlace", request.getLugarOEnlace())
                .addValue("ordenDelDia", request.getOrdenDelDia())
                .addValue("quorumRequeridoPct", quorumReq)
                .addValue("convocadaPor", idUsuario);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_ASAMBLEA"});

        Number key = keyHolder.getKey();
        if (key != null) return key.longValue();

        return jdbcTemplate.queryForObject(
                "SELECT ID_ASAMBLEA FROM ASAMBLEAS WHERE ID_PROPIEDAD = :idPropiedad AND TITULO = :titulo ORDER BY ID_ASAMBLEA DESC FETCH FIRST 1 ROWS ONLY",
                params, Long.class);
    }

    @Override
    public void updateEstado(Long idAsamblea, String nuevoEstado) {
        String sql = "UPDATE ASAMBLEAS SET ESTADO = :nuevoEstado WHERE ID_ASAMBLEA = :idAsamblea";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("nuevoEstado", nuevoEstado)
                .addValue("idAsamblea", idAsamblea));
    }

    @Override
    public void updateQuorumAlcanzado(Long idAsamblea, BigDecimal quorumAlcanzadoPct) {
        String sql = "UPDATE ASAMBLEAS SET QUORUM_ALCANZADO_PCT = :quorum WHERE ID_ASAMBLEA = :idAsamblea";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("quorum", quorumAlcanzadoPct)
                .addValue("idAsamblea", idAsamblea));
    }

    @Override
    public List<AsistenciaDTO> findAsistencias(Long idAsamblea) {
        String sql = "SELECT asi.ID_ASISTENCIA, asi.ID_ASAMBLEA, asi.ID_UNIDAD, u.IDENTIFICADOR as UNIDAD_IDENTIFICADOR, " +
                "asi.ID_PERSONA_ASISTENTE, " +
                "TRIM(NVL(p.PRIMER_NOMBRE, '') || ' ' || NVL(p.SEGUNDO_NOMBRE, '') || ' ' || NVL(p.PRIMER_APELLIDO, '') || ' ' || NVL(p.SEGUNDO_APELLIDO, '')) as NOMBRE_ASISTENTE, " +
                "p.NUMERO_DOCUMENTO as DOCUMENTO_ASISTENTE, " +
                "asi.ES_PROPIETARIO_DIRECTO, asi.ID_PODER, asi.COEFICIENTE_PONDERADO, asi.HORA_REGISTRO, asi.HORA_RETIRO " +
                "FROM ASISTENCIAS_ASAMBLEA asi " +
                "JOIN UNIDADES u ON asi.ID_UNIDAD = u.ID_UNIDAD " +
                "JOIN PERSONAS p ON asi.ID_PERSONA_ASISTENTE = p.ID_PERSONA " +
                "WHERE asi.ID_ASAMBLEA = :idAsamblea " +
                "ORDER BY asi.HORA_REGISTRO ASC";

        return jdbcTemplate.query(sql, new MapSqlParameterSource("idAsamblea", idAsamblea), asistenciaRowMapper);
    }

    @Override
    public Long registrarAsistencia(Long idAsamblea, AsistenciaRequestDTO request, BigDecimal coeficiente) {
        String sql = "INSERT INTO ASISTENCIAS_ASAMBLEA ( " +
                "ID_ASAMBLEA, ID_UNIDAD, ID_PERSONA_ASISTENTE, ES_PROPIETARIO_DIRECTO, " +
                "ID_PODER, COEFICIENTE_PONDERADO, HORA_REGISTRO " +
                ") VALUES ( " +
                ":idAsamblea, :idUnidad, :idPersonaAsistente, :esPropietarioDirecto, " +
                ":idPoder, :coeficiente, FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') " +
                ")";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idAsamblea", idAsamblea)
                .addValue("idUnidad", request.getIdUnidad())
                .addValue("idPersonaAsistente", request.getIdPersonaAsistente())
                .addValue("esPropietarioDirecto", request.getEsPropietarioDirecto())
                .addValue("idPoder", request.getIdPoder())
                .addValue("coeficiente", coeficiente);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_ASISTENCIA"});

        Number key = keyHolder.getKey();
        if (key != null) return key.longValue();

        return jdbcTemplate.queryForObject(
                "SELECT ID_ASISTENCIA FROM ASISTENCIAS_ASAMBLEA WHERE ID_ASAMBLEA = :idAsamblea AND ID_UNIDAD = :idUnidad ORDER BY ID_ASISTENCIA DESC FETCH FIRST 1 ROWS ONLY",
                params, Long.class);
    }

    @Override
    public void retirarAsistencia(Long idAsamblea, Long idUnidad) {
        String sql = "UPDATE ASISTENCIAS_ASAMBLEA " +
                "SET HORA_RETIRO = FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') " +
                "WHERE ID_ASAMBLEA = :idAsamblea AND ID_UNIDAD = :idUnidad AND HORA_RETIRO IS NULL";

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("idAsamblea", idAsamblea)
                .addValue("idUnidad", idUnidad));
    }

    @Override
    public BigDecimal calcularQuorumAlcanzadoPct(Long idAsamblea, Long idPropiedad) {
        String sqlAsist = "SELECT COALESCE(SUM(COEFICIENTE_PONDERADO), 0) FROM ASISTENCIAS_ASAMBLEA WHERE ID_ASAMBLEA = :idAsamblea AND HORA_RETIRO IS NULL";
        BigDecimal sumaAsistentes = jdbcTemplate.queryForObject(sqlAsist, new MapSqlParameterSource("idAsamblea", idAsamblea), BigDecimal.class);
        if (sumaAsistentes == null) sumaAsistentes = BigDecimal.ZERO;

        BigDecimal pct;
        if (sumaAsistentes.compareTo(BigDecimal.valueOf(1.0)) > 0) {
            // Ya viene en escala porcentual (ej. 52.50)
            pct = sumaAsistentes;
        } else {
            // Viene en escala decimal unitaria (ej. 0.525000 -> 52.50%)
            pct = sumaAsistentes.multiply(BigDecimal.valueOf(100));
        }

        // Capping seguro para respetar la restricción de base de datos NUMBER(5,2) (máx 100.00)
        if (pct.compareTo(BigDecimal.valueOf(100.00)) > 0) {
            pct = BigDecimal.valueOf(100.00);
        } else if (pct.compareTo(BigDecimal.ZERO) < 0) {
            pct = BigDecimal.ZERO;
        }

        return pct.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public List<PoderDTO> findPoderes(Long idAsamblea) {
        String sql = "SELECT pod.ID_PODER, pod.ID_ASAMBLEA, pod.ID_UNIDAD, u.IDENTIFICADOR as UNIDAD_IDENTIFICADOR, " +
                "pod.ID_PERSONA_PROPIETARIO, " +
                "TRIM(NVL(pp.PRIMER_NOMBRE, '') || ' ' || NVL(pp.SEGUNDO_NOMBRE, '') || ' ' || NVL(pp.PRIMER_APELLIDO, '') || ' ' || NVL(pp.SEGUNDO_APELLIDO, '')) as NOMBRE_PROPIETARIO, " +
                "pp.NUMERO_DOCUMENTO as DOCUMENTO_PROPIETARIO, " +
                "pod.ID_PERSONA_APODERADO, " +
                "TRIM(NVL(pa.PRIMER_NOMBRE, '') || ' ' || NVL(pa.SEGUNDO_NOMBRE, '') || ' ' || NVL(pa.PRIMER_APELLIDO, '') || ' ' || NVL(pa.SEGUNDO_APELLIDO, '')) as NOMBRE_APODERADO, " +
                "pa.NUMERO_DOCUMENTO as DOCUMENTO_APODERADO, " +
                "pod.DOCUMENTO_PODER_URL, pod.ESTADO, pod.VALIDADO_POR, pod.FECHA_REGISTRO " +
                "FROM PODERES_REPRESENTACION pod " +
                "JOIN UNIDADES u ON pod.ID_UNIDAD = u.ID_UNIDAD " +
                "JOIN PERSONAS pp ON pod.ID_PERSONA_PROPIETARIO = pp.ID_PERSONA " +
                "JOIN PERSONAS pa ON pod.ID_PERSONA_APODERADO = pa.ID_PERSONA " +
                "WHERE pod.ID_ASAMBLEA = :idAsamblea " +
                "ORDER BY pod.FECHA_REGISTRO DESC";

        return jdbcTemplate.query(sql, new MapSqlParameterSource("idAsamblea", idAsamblea), poderRowMapper);
    }

    @Override
    public Long registrarPoder(Long idAsamblea, PoderRequestDTO request) {
        String sql = "INSERT INTO PODERES_REPRESENTACION ( " +
                "ID_ASAMBLEA, ID_UNIDAD, ID_PERSONA_PROPIETARIO, ID_PERSONA_APODERADO, " +
                "DOCUMENTO_PODER_URL, ESTADO, FECHA_REGISTRO " +
                ") VALUES ( " +
                ":idAsamblea, :idUnidad, :idPersonaPropietario, :idPersonaApoderado, " +
                ":documentoPoderUrl, 'PENDIENTE_REVISION', FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') " +
                ")";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idAsamblea", idAsamblea)
                .addValue("idUnidad", request.getIdUnidad())
                .addValue("idPersonaPropietario", request.getIdPersonaPropietario())
                .addValue("idPersonaApoderado", request.getIdPersonaApoderado())
                .addValue("documentoPoderUrl", request.getDocumentoPoderUrl());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PODER"});

        Number key = keyHolder.getKey();
        if (key != null) return key.longValue();

        return jdbcTemplate.queryForObject(
                "SELECT ID_PODER FROM PODERES_REPRESENTACION WHERE ID_ASAMBLEA = :idAsamblea AND ID_UNIDAD = :idUnidad ORDER BY ID_PODER DESC FETCH FIRST 1 ROWS ONLY",
                params, Long.class);
    }

    @Override
    public void decidirPoder(Long idPoder, String nuevoEstado, Long idUsuarioValidador) {
        String sql = "UPDATE PODERES_REPRESENTACION " +
                "SET ESTADO = :nuevoEstado, VALIDADO_POR = :idValidador " +
                "WHERE ID_PODER = :idPoder";

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("nuevoEstado", nuevoEstado)
                .addValue("idValidador", idUsuarioValidador)
                .addValue("idPoder", idPoder));
    }

    @Override
    public List<VotacionDTO> findVotaciones(Long idAsamblea) {
        String sql = "SELECT v.ID_VOTACION, v.ID_ASAMBLEA, v.PUNTO_ORDEN_DIA, v.TITULO, v.DESCRIPCION, " +
                "v.TIPO_MAYORIA_REQUERIDA, v.HORA_APERTURA, v.HORA_CIERRE, v.ESTADO, " +
                "COALESCE(SUM(CASE WHEN vt.OPCION_VOTO = 'SI' THEN vt.COEFICIENTE_VOTO ELSE 0 END), 0) as VOTOS_SI, " +
                "COALESCE(SUM(CASE WHEN vt.OPCION_VOTO = 'NO' THEN vt.COEFICIENTE_VOTO ELSE 0 END), 0) as VOTOS_NO, " +
                "COALESCE(SUM(CASE WHEN vt.OPCION_VOTO = 'BLANCO' THEN vt.COEFICIENTE_VOTO ELSE 0 END), 0) as VOTOS_BLANCO, " +
                "COALESCE(SUM(CASE WHEN vt.OPCION_VOTO = 'ABSTENCION' THEN vt.COEFICIENTE_VOTO ELSE 0 END), 0) as VOTOS_ABSTENCION, " +
                "COUNT(vt.ID_VOTO) as TOTAL_VOTOS " +
                "FROM VOTACIONES v " +
                "LEFT JOIN VOTOS vt ON v.ID_VOTACION = vt.ID_VOTACION " +
                "WHERE v.ID_ASAMBLEA = :idAsamblea " +
                "GROUP BY v.ID_VOTACION, v.ID_ASAMBLEA, v.PUNTO_ORDEN_DIA, v.TITULO, v.DESCRIPCION, " +
                "         v.TIPO_MAYORIA_REQUERIDA, v.HORA_APERTURA, v.HORA_CIERRE, v.ESTADO " +
                "ORDER BY v.PUNTO_ORDEN_DIA ASC";

        return jdbcTemplate.query(sql, new MapSqlParameterSource("idAsamblea", idAsamblea), votacionRowMapper);
    }

    @Override
    public Optional<VotacionDTO> findVotacionById(Long idVotacion) {
        String sql = "SELECT v.ID_VOTACION, v.ID_ASAMBLEA, v.PUNTO_ORDEN_DIA, v.TITULO, v.DESCRIPCION, " +
                "v.TIPO_MAYORIA_REQUERIDA, v.HORA_APERTURA, v.HORA_CIERRE, v.ESTADO, " +
                "COALESCE(SUM(CASE WHEN vt.OPCION_VOTO = 'SI' THEN vt.COEFICIENTE_VOTO ELSE 0 END), 0) as VOTOS_SI, " +
                "COALESCE(SUM(CASE WHEN vt.OPCION_VOTO = 'NO' THEN vt.COEFICIENTE_VOTO ELSE 0 END), 0) as VOTOS_NO, " +
                "COALESCE(SUM(CASE WHEN vt.OPCION_VOTO = 'BLANCO' THEN vt.COEFICIENTE_VOTO ELSE 0 END), 0) as VOTOS_BLANCO, " +
                "COALESCE(SUM(CASE WHEN vt.OPCION_VOTO = 'ABSTENCION' THEN vt.COEFICIENTE_VOTO ELSE 0 END), 0) as VOTOS_ABSTENCION, " +
                "COUNT(vt.ID_VOTO) as TOTAL_VOTOS " +
                "FROM VOTACIONES v " +
                "LEFT JOIN VOTOS vt ON v.ID_VOTACION = vt.ID_VOTACION " +
                "WHERE v.ID_VOTACION = :idVotacion " +
                "GROUP BY v.ID_VOTACION, v.ID_ASAMBLEA, v.PUNTO_ORDEN_DIA, v.TITULO, v.DESCRIPCION, " +
                "         v.TIPO_MAYORIA_REQUERIDA, v.HORA_APERTURA, v.HORA_CIERRE, v.ESTADO";

        try {
            VotacionDTO dto = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idVotacion", idVotacion), votacionRowMapper);
            return Optional.ofNullable(dto);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Long createVotacion(Long idAsamblea, VotacionCreateRequestDTO request) {
        String sql = "INSERT INTO VOTACIONES ( " +
                "ID_ASAMBLEA, PUNTO_ORDEN_DIA, TITULO, DESCRIPCION, " +
                "TIPO_MAYORIA_REQUERIDA, HORA_APERTURA, ESTADO " +
                ") VALUES ( " +
                ":idAsamblea, :puntoOrdenDia, :titulo, :descripcion, " +
                ":tipoMayoriaRequerida, FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota'), 'ABIERTA' " +
                ")";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idAsamblea", idAsamblea)
                .addValue("puntoOrdenDia", request.getPuntoOrdenDia())
                .addValue("titulo", request.getTitulo())
                .addValue("descripcion", request.getDescripcion())
                .addValue("tipoMayoriaRequerida", request.getTipoMayoriaRequerida());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_VOTACION"});

        Number key = keyHolder.getKey();
        if (key != null) return key.longValue();

        return jdbcTemplate.queryForObject(
                "SELECT ID_VOTACION FROM VOTACIONES WHERE ID_ASAMBLEA = :idAsamblea AND PUNTO_ORDEN_DIA = :puntoOrdenDia ORDER BY ID_VOTACION DESC FETCH FIRST 1 ROWS ONLY",
                params, Long.class);
    }

    @Override
    public void cerrarVotacion(Long idVotacion) {
        String sql = "UPDATE VOTACIONES " +
                "SET ESTADO = 'CERRADA', HORA_CIERRE = FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') " +
                "WHERE ID_VOTACION = :idVotacion";

        jdbcTemplate.update(sql, new MapSqlParameterSource("idVotacion", idVotacion));
    }

    @Override
    public Long registrarVoto(Long idVotacion, VotoRequestDTO request, BigDecimal coeficiente) {
        String sql = "INSERT INTO VOTOS ( " +
                "ID_VOTACION, ID_UNIDAD, ID_PERSONA_VOTANTE, OPCION_VOTO, " +
                "COEFICIENTE_VOTO, FECHA_HORA_VOTO " +
                ") VALUES ( " +
                ":idVotacion, :idUnidad, :idPersonaVotante, :opcionVoto, " +
                ":coeficiente, FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') " +
                ")";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idVotacion", idVotacion)
                .addValue("idUnidad", request.getIdUnidad())
                .addValue("idPersonaVotante", request.getIdPersonaVotante())
                .addValue("opcionVoto", request.getOpcionVoto())
                .addValue("coeficiente", coeficiente);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_VOTO"});

        Number key = keyHolder.getKey();
        if (key != null) return key.longValue();

        return jdbcTemplate.queryForObject(
                "SELECT ID_VOTO FROM VOTOS WHERE ID_VOTACION = :idVotacion AND ID_UNIDAD = :idUnidad ORDER BY ID_VOTO DESC FETCH FIRST 1 ROWS ONLY",
                params, Long.class);
    }

    @Override
    public BigDecimal findCoeficienteUnidad(Long idUnidad) {
        String sql = "SELECT COEFICIENTE_COPROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = :idUnidad";
        try {
            BigDecimal coef = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idUnidad", idUnidad), BigDecimal.class);
            return (coef != null && coef.compareTo(BigDecimal.ZERO) > 0) ? coef : BigDecimal.valueOf(0.010000);
        } catch (EmptyResultDataAccessException e) {
            return BigDecimal.valueOf(0.010000);
        }
    }

    @Override
    public boolean existeAsistenciaUnidad(Long idAsamblea, Long idUnidad) {
        String sql = "SELECT COUNT(1) FROM ASISTENCIAS_ASAMBLEA WHERE ID_ASAMBLEA = :idAsamblea AND ID_UNIDAD = :idUnidad";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("idAsamblea", idAsamblea)
                .addValue("idUnidad", idUnidad), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existeVotoUnidad(Long idVotacion, Long idUnidad) {
        String sql = "SELECT COUNT(1) FROM VOTOS WHERE ID_VOTACION = :idVotacion AND ID_UNIDAD = :idUnidad";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("idVotacion", idVotacion)
                .addValue("idUnidad", idUnidad), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existePoderUnidad(Long idAsamblea, Long idUnidad) {
        String sql = "SELECT COUNT(1) FROM PODERES_REPRESENTACION WHERE ID_ASAMBLEA = :idAsamblea AND ID_UNIDAD = :idUnidad";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("idAsamblea", idAsamblea)
                .addValue("idUnidad", idUnidad), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public int countUnidadesActivas(Long idPropiedad) {
        String sql = "SELECT COUNT(1) FROM UNIDADES WHERE ID_PROPIEDAD = :idPropiedad AND ESTADO = 'ACTIVA'";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idPropiedad", idPropiedad), Integer.class);
        return count != null ? count : 0;
    }
}
