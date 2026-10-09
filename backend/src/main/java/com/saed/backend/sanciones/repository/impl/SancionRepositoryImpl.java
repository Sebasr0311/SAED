package com.saed.backend.sanciones.repository.impl;

import com.saed.backend.sanciones.dto.DescargoDTO;
import com.saed.backend.sanciones.dto.SancionCreateRequestDTO;
import com.saed.backend.sanciones.dto.SancionDTO;
import com.saed.backend.sanciones.repository.SancionRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;

@Repository
public class SancionRepositoryImpl implements SancionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SancionRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<SancionDTO> sancionRowMapper = (rs, rowNum) -> {
        SancionDTO dto = new SancionDTO();
        dto.setIdSancion(rs.getLong("ID_SANCION"));
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        dto.setIdUnidad(rs.getLong("ID_UNIDAD"));
        dto.setIdentificadorUnidad(rs.getString("UNIDAD_IDENTIFICADOR"));
        dto.setIdPersonaImputada(rs.getLong("ID_PERSONA_IMPUTADA"));
        dto.setNombreImputado(rs.getString("NOMBRE_IMPUTADO"));

        long incId = rs.getLong("ID_INCIDENTE_ORIGEN");
        if (!rs.wasNull()) dto.setIdIncidenteOrigen(incId);

        dto.setNumeroExpediente(rs.getString("NUMERO_EXPEDIENTE"));
        dto.setTipoFalta(rs.getString("TIPO_FALTA"));
        dto.setGravedad(rs.getString("GRAVEDAD"));
        dto.setDescripcionHechos(rs.getString("DESCRIPCION_HECHOS"));
        dto.setArticuloReglamentoViolado(rs.getString("ARTICULO_REGLAMENTO_VIOLADO"));
        dto.setEvidenciasUrls(rs.getString("EVIDENCIAS_URLS"));
        dto.setTipoSancionPropuesta(rs.getString("TIPO_SANCION_PROPUESTA"));

        if (rs.getTimestamp("FECHA_APERTURA_PLIEGO") != null) {
            dto.setFechaAperturaPliego(rs.getTimestamp("FECHA_APERTURA_PLIEGO").toLocalDateTime());
        }
        if (rs.getDate("FECHA_LIMITE_DESCARGOS") != null) {
            dto.setFechaLimiteDescargos(rs.getDate("FECHA_LIMITE_DESCARGOS").toLocalDate());
        }

        dto.setResolucionFinal(rs.getString("RESOLUCION_FINAL"));
        if (rs.getTimestamp("FECHA_RESOLUCION") != null) {
            dto.setFechaResolucion(rs.getTimestamp("FECHA_RESOLUCION").toLocalDateTime());
        }

        dto.setEstado(rs.getString("ESTADO"));

        long creador = rs.getLong("CREADO_POR");
        if (!rs.wasNull()) dto.setCreadoPor(creador);

        if (rs.getDate("RESTRICCION_DESDE") != null) {
            dto.setRestriccionDesde(rs.getDate("RESTRICCION_DESDE").toLocalDate());
        }
        if (rs.getDate("RESTRICCION_HASTA") != null) {
            dto.setRestriccionHasta(rs.getDate("RESTRICCION_HASTA").toLocalDate());
        }

        return dto;
    };

    private final RowMapper<DescargoDTO> descargoRowMapper = (rs, rowNum) -> {
        DescargoDTO dto = new DescargoDTO();
        dto.setIdDescargo(rs.getLong("ID_DESCARGO"));
        dto.setIdSancion(rs.getLong("ID_SANCION"));
        dto.setIdPersonaPresenta(rs.getLong("ID_PERSONA_PRESENTA"));
        dto.setNombrePresenta(rs.getString("NOMBRE_PRESENTA"));
        dto.setArgumentosDefensa(rs.getString("ARGUMENTOS_DEFENSA"));
        dto.setPruebasAdjuntasUrl(rs.getString("PRUEBAS_ADJUNTAS_URL"));

        if (rs.getTimestamp("FECHA_PRESENTACION") != null) {
            dto.setFechaPresentacion(rs.getTimestamp("FECHA_PRESENTACION").toLocalDateTime());
        }

        long radicado = rs.getLong("RADICADO_POR_USUARIO");
        if (!rs.wasNull()) dto.setRadicadoPorUsuario(radicado);

        return dto;
    };

    @Override
    public List<SancionDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = "SELECT s.ID_SANCION, s.ID_PROPIEDAD, s.ID_UNIDAD, " +
                "u.IDENTIFICADOR as UNIDAD_IDENTIFICADOR, s.ID_PERSONA_IMPUTADA, " +
                "TRIM(NVL(p.PRIMER_NOMBRE, '') || ' ' || NVL(p.SEGUNDO_NOMBRE, '') || ' ' || NVL(p.PRIMER_APELLIDO, '') || ' ' || NVL(p.SEGUNDO_APELLIDO, '')) as NOMBRE_IMPUTADO, " +
                "s.ID_INCIDENTE_ORIGEN, s.NUMERO_EXPEDIENTE, s.TIPO_FALTA, s.GRAVEDAD, s.DESCRIPCION_HECHOS, " +
                "s.ARTICULO_REGLAMENTO_VIOLADO, s.EVIDENCIAS_URLS, s.TIPO_SANCION_PROPUESTA, " +
                "s.FECHA_APERTURA_PLIEGO, s.FECHA_LIMITE_DESCARGOS, s.RESOLUCION_FINAL, s.FECHA_RESOLUCION, " +
                "s.ESTADO, s.CREADO_POR, s.RESTRICCION_DESDE, s.RESTRICCION_HASTA " +
                "FROM SANCIONES s " +
                "LEFT JOIN UNIDADES u ON s.ID_UNIDAD = u.ID_UNIDAD " +
                "LEFT JOIN PERSONAS p ON s.ID_PERSONA_IMPUTADA = p.ID_PERSONA " +
                "WHERE (:propId IS NULL OR s.ID_PROPIEDAD = :propId) " +
                "ORDER BY s.FECHA_APERTURA_PLIEGO DESC";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("propId", idPropiedad, Types.NUMERIC);
        return jdbc.query(sql, params, sancionRowMapper);
    }

    @Override
    public List<SancionDTO> findByPersonaOUnidades(Long idPersona, List<Long> unidadesIds, Long idPropiedad) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.ID_SANCION, s.ID_PROPIEDAD, s.ID_UNIDAD, ")
           .append("u.IDENTIFICADOR as UNIDAD_IDENTIFICADOR, s.ID_PERSONA_IMPUTADA, ")
           .append("TRIM(NVL(p.PRIMER_NOMBRE, '') || ' ' || NVL(p.SEGUNDO_NOMBRE, '') || ' ' || NVL(p.PRIMER_APELLIDO, '') || ' ' || NVL(p.SEGUNDO_APELLIDO, '')) as NOMBRE_IMPUTADO, ")
           .append("s.ID_INCIDENTE_ORIGEN, s.NUMERO_EXPEDIENTE, s.TIPO_FALTA, s.GRAVEDAD, s.DESCRIPCION_HECHOS, ")
           .append("s.ARTICULO_REGLAMENTO_VIOLADO, s.EVIDENCIAS_URLS, s.TIPO_SANCION_PROPUESTA, ")
           .append("s.FECHA_APERTURA_PLIEGO, s.FECHA_LIMITE_DESCARGOS, s.RESOLUCION_FINAL, s.FECHA_RESOLUCION, ")
           .append("s.ESTADO, s.CREADO_POR, s.RESTRICCION_DESDE, s.RESTRICCION_HASTA ")
           .append("FROM SANCIONES s ")
           .append("LEFT JOIN UNIDADES u ON s.ID_UNIDAD = u.ID_UNIDAD ")
           .append("LEFT JOIN PERSONAS p ON s.ID_PERSONA_IMPUTADA = p.ID_PERSONA ")
           .append("WHERE (:propId IS NULL OR s.ID_PROPIEDAD = :propId) ")
           .append("AND (s.ID_PERSONA_IMPUTADA = :idPersona ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", idPropiedad, Types.NUMERIC)
                .addValue("idPersona", idPersona, Types.NUMERIC);

        if (unidadesIds != null && !unidadesIds.isEmpty()) {
            sql.append(" OR s.ID_UNIDAD IN (:unidadesIds)");
            params.addValue("unidadesIds", unidadesIds);
        }
        sql.append(") ORDER BY s.FECHA_APERTURA_PLIEGO DESC");

        return jdbc.query(sql.toString(), params, sancionRowMapper);
    }

    @Override
    public Optional<SancionDTO> findById(Long idSancion) {
        String sql = "SELECT s.ID_SANCION, s.ID_PROPIEDAD, s.ID_UNIDAD, " +
                "u.IDENTIFICADOR as UNIDAD_IDENTIFICADOR, s.ID_PERSONA_IMPUTADA, " +
                "TRIM(NVL(p.PRIMER_NOMBRE, '') || ' ' || NVL(p.SEGUNDO_NOMBRE, '') || ' ' || NVL(p.PRIMER_APELLIDO, '') || ' ' || NVL(p.SEGUNDO_APELLIDO, '')) as NOMBRE_IMPUTADO, " +
                "s.ID_INCIDENTE_ORIGEN, s.NUMERO_EXPEDIENTE, s.TIPO_FALTA, s.GRAVEDAD, s.DESCRIPCION_HECHOS, " +
                "s.ARTICULO_REGLAMENTO_VIOLADO, s.EVIDENCIAS_URLS, s.TIPO_SANCION_PROPUESTA, " +
                "s.FECHA_APERTURA_PLIEGO, s.FECHA_LIMITE_DESCARGOS, s.RESOLUCION_FINAL, s.FECHA_RESOLUCION, " +
                "s.ESTADO, s.CREADO_POR, s.RESTRICCION_DESDE, s.RESTRICCION_HASTA " +
                "FROM SANCIONES s " +
                "LEFT JOIN UNIDADES u ON s.ID_UNIDAD = u.ID_UNIDAD " +
                "LEFT JOIN PERSONAS p ON s.ID_PERSONA_IMPUTADA = p.ID_PERSONA " +
                "WHERE s.ID_SANCION = :id";

        List<SancionDTO> list = jdbc.query(sql, new MapSqlParameterSource("id", idSancion), sancionRowMapper);
        return list.stream().findFirst();
    }

    @Override
    public Long crearSancion(SancionCreateRequestDTO req, Long idPropiedad, Long idUsuarioCreador, String numeroExpediente, LocalDate fechaLimiteDescargos) {
        String sql = "INSERT INTO SANCIONES (" +
                "ID_PROPIEDAD, ID_UNIDAD, ID_PERSONA_IMPUTADA, ID_INCIDENTE_ORIGEN, " +
                "NUMERO_EXPEDIENTE, TIPO_FALTA, GRAVEDAD, DESCRIPCION_HECHOS, " +
                "ARTICULO_REGLAMENTO_VIOLADO, EVIDENCIAS_URLS, TIPO_SANCION_PROPUESTA, " +
                "FECHA_APERTURA_PLIEGO, FECHA_LIMITE_DESCARGOS, ESTADO, CREADO_POR" +
                ") VALUES (" +
                ":idPropiedad, :idUnidad, :idPersona, :idIncidente, " +
                ":expediente, :tipoFalta, :gravedad, :hechos, " +
                ":articulo, :evidencias, :tipoSancion, " +
                "CURRENT_TIMESTAMP, :fechaLimite, 'NOTIFICADA', :creador)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", idPropiedad)
                .addValue("idUnidad", req.getIdUnidad())
                .addValue("idPersona", req.getIdPersonaImputada())
                .addValue("idIncidente", req.getIdIncidenteOrigen())
                .addValue("expediente", numeroExpediente)
                .addValue("tipoFalta", req.getTipoFalta())
                .addValue("gravedad", req.getGravedad() != null ? req.getGravedad() : "LEVE")
                .addValue("hechos", req.getDescripcionHechos())
                .addValue("articulo", req.getArticuloReglamentoViolado())
                .addValue("evidencias", req.getEvidenciasUrls())
                .addValue("tipoSancion", req.getTipoSancionPropuesta() != null ? req.getTipoSancionPropuesta() : "AMONESTACION_ESCRITA")
                .addValue("fechaLimite", Date.valueOf(fechaLimiteDescargos))
                .addValue("creador", idUsuarioCreador);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"ID_SANCION"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    @Override
    public Long registrarDescargo(Long idSancion, Long idPersona, Long idUsuario, String argumentos, String pruebasUrl) {
        String sql = "INSERT INTO SANCION_DESCARGOS (" +
                "ID_SANCION, ID_PERSONA_PRESENTA, ARGUMENTOS_DEFENSA, " +
                "PRUEBAS_ADJUNTAS_URL, FECHA_PRESENTACION, RADICADO_POR_USUARIO" +
                ") VALUES (" +
                ":idSancion, :idPersona, :argumentos, " +
                ":pruebasUrl, CURRENT_TIMESTAMP, :idUsuario)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idSancion", idSancion)
                .addValue("idPersona", idPersona)
                .addValue("argumentos", argumentos)
                .addValue("pruebasUrl", pruebasUrl)
                .addValue("idUsuario", idUsuario);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"ID_DESCARGO"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    @Override
    public void actualizarEstado(Long idSancion, String nuevoEstado) {
        String sql = "UPDATE SANCIONES SET ESTADO = :estado WHERE ID_SANCION = :id";
        jdbc.update(sql, new MapSqlParameterSource("estado", nuevoEstado).addValue("id", idSancion));
    }

    @Override
    public List<DescargoDTO> findDescargosBySancion(Long idSancion) {
        String sql = "SELECT d.ID_DESCARGO, d.ID_SANCION, d.ID_PERSONA_PRESENTA, " +
                "TRIM(NVL(p.PRIMER_NOMBRE, '') || ' ' || NVL(p.SEGUNDO_NOMBRE, '') || ' ' || NVL(p.PRIMER_APELLIDO, '') || ' ' || NVL(p.SEGUNDO_APELLIDO, '')) as NOMBRE_PRESENTA, " +
                "d.ARGUMENTOS_DEFENSA, d.PRUEBAS_ADJUNTAS_URL, d.FECHA_PRESENTACION, d.RADICADO_POR_USUARIO " +
                "FROM SANCION_DESCARGOS d " +
                "LEFT JOIN PERSONAS p ON d.ID_PERSONA_PRESENTA = p.ID_PERSONA " +
                "WHERE d.ID_SANCION = :idSancion " +
                "ORDER BY d.FECHA_PRESENTACION ASC";

        return jdbc.query(sql, new MapSqlParameterSource("idSancion", idSancion), descargoRowMapper);
    }

    @Override
    public String generarSiguienteExpediente(Long idPropiedad) {
        int year = Year.now().getValue();
        String countSql = "SELECT NVL(COUNT(*), 0) + 1 FROM SANCIONES WHERE ID_PROPIEDAD = :propId";
        Integer nextSeq = jdbc.queryForObject(countSql, new MapSqlParameterSource().addValue("propId", idPropiedad, Types.NUMERIC), Integer.class);
        if (nextSeq == null) nextSeq = 1;
        return String.format("EXP-%d-P%d-%04d", year, idPropiedad != null ? idPropiedad : 1L, nextSeq);
    }

    @Override
    public Optional<Long> findConceptoMulta(Long idPropiedad) {
        String sql = "SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO " +
                "WHERE (:propId IS NULL OR ID_PROPIEDAD = :propId OR ID_PROPIEDAD IS NULL) " +
                "AND ESTADO = 'ACTIVO' AND (UPPER(TIPO) LIKE '%MULTA%' OR UPPER(CODIGO) LIKE '%MULTA%') " +
                "ORDER BY CASE WHEN ID_PROPIEDAD = :propId THEN 1 WHEN ID_PROPIEDAD IS NULL THEN 2 ELSE 3 END, ID_CONCEPTO ASC";
        List<Long> ids = jdbc.query(sql, new MapSqlParameterSource().addValue("propId", idPropiedad, Types.NUMERIC), (rs, rowNum) -> rs.getLong("ID_CONCEPTO"));
        if (!ids.isEmpty()) return Optional.of(ids.get(0));

        return Optional.empty();
    }

    @Override
    public Long crearMultaDesdeSancion(Long idSancion, Long idUnidad, Long idPersona, Long idConcepto, BigDecimal monto, String motivo, Long idUsuario) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto de la multa debe ser mayor a 0");
        }
        String sql = "INSERT INTO MULTAS (" +
                "ID_UNIDAD, ID_PERSONA_INFRACTORA, ID_CONCEPTO, MONTO, MOTIVO, " +
                "ID_SANCION_ORIGEN, ESTADO, IMPUESTA_POR, FECHA_IMPOSICION" +
                ") VALUES (" +
                ":idUnidad, :idPersona, :idConcepto, :monto, :motivo, " +
                ":idSancion, 'IMPUESTA', :idUsuario, CURRENT_TIMESTAMP)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("idUnidad", idUnidad)
                .addValue("idPersona", idPersona)
                .addValue("idConcepto", idConcepto)
                .addValue("monto", monto)
                .addValue("motivo", motivo != null ? motivo : "Sanción económica derivada de proceso disciplinario")
                .addValue("idSancion", idSancion)
                .addValue("idUsuario", idUsuario), keyHolder, new String[]{"ID_MULTA"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public int emitirResolucionSiAbierta(Long idSancion, String decision, String resolucionFinal,
                                         LocalDate restriccionDesde, LocalDate restriccionHasta) {
        String sql = "UPDATE SANCIONES SET " +
                "ESTADO = :decision, " +
                "RESOLUCION_FINAL = :resolucion, " +
                "FECHA_RESOLUCION = CURRENT_TIMESTAMP, " +
                "RESTRICCION_DESDE = :desde, " +
                "RESTRICCION_HASTA = :hasta " +
                "WHERE ID_SANCION = :id AND ESTADO IN ('NOTIFICADA', 'EN_DESCARGOS', 'EN_DELIBERACION_CONSEJO')";

        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("decision", decision)
                .addValue("resolucion", resolucionFinal)
                .addValue("desde", restriccionDesde != null ? Date.valueOf(restriccionDesde) : null, Types.DATE)
                .addValue("hasta", restriccionHasta != null ? Date.valueOf(restriccionHasta) : null, Types.DATE)
                .addValue("id", idSancion));
    }

    @Override
    public Optional<Long> findMultaPorSancion(Long idSancion) {
        String sql = "SELECT ID_MULTA FROM MULTAS WHERE ID_SANCION_ORIGEN = :idSancion ORDER BY ID_MULTA";
        List<Long> ids = jdbc.queryForList(sql, new MapSqlParameterSource("idSancion", idSancion), Long.class);
        return ids.stream().findFirst();
    }

    @Override
    public void vincularCuotaMulta(Long idMulta, Long idCuota) {
        String sql = "UPDATE MULTAS SET ID_CUOTA = :idCuota WHERE ID_MULTA = :idMulta AND ID_CUOTA IS NULL";
        jdbc.update(sql, new MapSqlParameterSource().addValue("idCuota", idCuota).addValue("idMulta", idMulta));
    }

    @Override
    public boolean existeRestriccionZonasComunes(Long idUnidad, LocalDate fecha) {
        String sql = "SELECT COUNT(1) FROM SANCIONES " +
                "WHERE ID_UNIDAD = :idUnidad AND ESTADO = 'APLICADA' " +
                "AND TIPO_SANCION_PROPUESTA = 'SUSPENSION_ZONAS_COMUNES' " +
                "AND RESTRICCION_DESDE IS NOT NULL AND RESTRICCION_HASTA IS NOT NULL " +
                "AND :fecha BETWEEN RESTRICCION_DESDE AND RESTRICCION_HASTA";
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("idUnidad", idUnidad)
                .addValue("fecha", Date.valueOf(fecha), Types.DATE), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Long findPersonaUsuario(Long idUsuario) {
        List<Long> ids = jdbc.queryForList("SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :id",
                new MapSqlParameterSource("id", idUsuario), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    public boolean personaVinculadaAUnidad(Long idPersona, Long idUnidad) {
        if (idPersona == null || idUnidad == null) {
            return false;
        }
        String sql = "SELECT " +
                "(SELECT COUNT(1) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = :idUnidad AND ID_PERSONA = :idPersona " +
                " AND ESTADO = 'ACTIVO' AND (FECHA_FIN IS NULL OR FECHA_FIN >= TRUNC(SYSDATE))) + " +
                "(SELECT COUNT(1) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = :idUnidad AND ID_PERSONA = :idPersona " +
                " AND NVL(ESTADO, 'ACTIVO') = 'ACTIVO') FROM DUAL";
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("idUnidad", idUnidad)
                .addValue("idPersona", idPersona), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public List<Long> findUnidadesPersona(Long idPersona) {
        String sql = "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = :pId AND NVL(ESTADO, 'ACTIVO') = 'ACTIVO' " +
                "UNION " +
                "SELECT ID_UNIDAD FROM PROPIETARIOS_UNIDAD WHERE ID_PERSONA = :pId AND ESTADO = 'ACTIVO'";
        return jdbc.queryForList(sql, new MapSqlParameterSource("pId", idPersona), Long.class);
    }

    @Override
    public Long findPropiedadUnidad(Long idUnidad) {
        if (idUnidad == null) {
            return null;
        }
        List<Long> ids = jdbc.queryForList("SELECT ID_PROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = :id",
                new MapSqlParameterSource("id", idUnidad), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
