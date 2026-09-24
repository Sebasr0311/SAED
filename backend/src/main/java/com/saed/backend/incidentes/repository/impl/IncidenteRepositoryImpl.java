package com.saed.backend.incidentes.repository.impl;

import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.dto.IncidenteInvolucradoDTO;
import com.saed.backend.incidentes.repository.IncidenteRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
public class IncidenteRepositoryImpl implements IncidenteRepository {

    private final JdbcTemplate jdbcTemplate;

    public IncidenteRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<IncidenteDTO> rowMapper = (rs, rowNum) -> {
        IncidenteDTO dto = new IncidenteDTO();
        dto.setIdIncidente(rs.getLong("ID_INCIDENTE"));
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        
        long idPorteria = rs.getLong("ID_PORTERIA");
        if (!rs.wasNull()) dto.setIdPorteria(idPorteria);
        
        long idZona = rs.getLong("ID_ZONA_COMUN");
        if (!rs.wasNull()) dto.setIdZonaComun(idZona);
        
        long idUnidad = rs.getLong("ID_UNIDAD");
        if (!rs.wasNull()) dto.setIdUnidad(idUnidad);
        
        dto.setTitulo(rs.getString("TITULO"));
        dto.setTipoIncidente(rs.getString("TIPO_INCIDENTE"));
        dto.setNivelSeveridad(rs.getString("NIVEL_SEVERIDAD"));
        dto.setDescripcionHechos(rs.getString("DESCRIPCION_HECHOS"));
        
        Timestamp fhi = rs.getTimestamp("FECHA_HORA_INCIDENTE");
        if (fhi != null) dto.setFechaHoraIncidente(fhi.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
        
        dto.setRegistradoPor(rs.getLong("REGISTRADO_POR"));
        dto.setRequirioAutoridades(rs.getString("REQUIRIO_AUTORIDADES"));
        dto.setEntidadAutoridad(rs.getString("ENTIDAD_AUTORIDAD"));
        dto.setNumeroDenunciaPolicia(rs.getString("NUMERO_DENUNCIA_POLICIA"));
        dto.setEvidenciasUrls(rs.getString("EVIDENCIAS_URLS"));
        dto.setAccionesInmediatas(rs.getString("ACCIONES_INMEDIATAS"));
        dto.setEstado(rs.getString("ESTADO"));
        
        Timestamp fc = rs.getTimestamp("FECHA_CIERRE");
        if (fc != null) dto.setFechaCierre(fc.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
        
        dto.setConclusionesCierre(rs.getString("CONCLUSIONES_CIERRE"));
        
        Timestamp fr = rs.getTimestamp("FECHA_REGISTRO");
        if (fr != null) dto.setFechaRegistro(fr.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));

        // Pipeline investigacion
        try {
            long invPor = rs.getLong("INVESTIGADO_POR");
            if (!rs.wasNull()) dto.setInvestigadoPor(invPor);
            Timestamp fii = rs.getTimestamp("FECHA_INICIO_INVESTIGACION");
            if (fii != null) dto.setFechaInicioInvestigacion(fii.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
            Timestamp ffi = rs.getTimestamp("FECHA_FIN_INVESTIGACION");
            if (ffi != null) dto.setFechaFinInvestigacion(ffi.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
            dto.setHallazgosInvestigacion(rs.getString("HALLAZGOS_INVESTIGACION"));
        } catch (Exception ignored) {}

        // Pipeline escalamiento
        try {
            long escPor = rs.getLong("ESCALADO_POR");
            if (!rs.wasNull()) dto.setEscaladoPor(escPor);
            Timestamp fe = rs.getTimestamp("FECHA_ESCALAMIENTO");
            if (fe != null) dto.setFechaEscalamiento(fe.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));
            dto.setMotivoEscalamiento(rs.getString("MOTIVO_ESCALAMIENTO"));
            dto.setSancionSugerida(rs.getString("SANCION_SUGERIDA"));
        } catch (Exception ignored) {}

        // Visual helpers
        try {
            dto.setUnidadIdentificador(rs.getString("UNIDAD_IDENTIFICADOR"));
        } catch (Exception ignored) {}
        try {
            dto.setNombreRegistradoPor(rs.getString("REGISTRADO_POR_NOMBRE"));
        } catch (Exception ignored) {}
        
        return dto;
    };

    private final RowMapper<IncidenteInvolucradoDTO> involucradoRowMapper = (rs, rowNum) -> {
        IncidenteInvolucradoDTO dto = new IncidenteInvolucradoDTO();
        dto.setIdIncidenteInvolucrado(rs.getLong("ID_INCIDENTE_INVOLUCRADO"));
        dto.setIdIncidente(rs.getLong("ID_INCIDENTE"));

        long idPersona = rs.getLong("ID_PERSONA");
        if (!rs.wasNull()) dto.setIdPersona(idPersona);

        long idVehiculo = rs.getLong("ID_VEHICULO");
        if (!rs.wasNull()) dto.setIdVehiculo(idVehiculo);

        dto.setNombreIdentificacionExterna(rs.getString("NOMBRE_IDENTIFICACION_EXTERNA"));
        dto.setRolEnIncidente(rs.getString("ROL_EN_INCIDENTE"));
        dto.setDeclaracionTestimonio(rs.getString("DECLARACION_TESTIMONIO"));

        try {
            dto.setNombrePersona(rs.getString("NOMBRE_PERSONA"));
        } catch (Exception ignored) {}
        try {
            dto.setDocumentoPersona(rs.getString("DOCUMENTO_PERSONA"));
        } catch (Exception ignored) {}
        try {
            dto.setPlacaVehiculo(rs.getString("PLACA_VEHICULO"));
        } catch (Exception ignored) {}

        return dto;
    };

    private static final String SELECT_BASE = 
        "SELECT i.*, u.IDENTIFICADOR AS UNIDAD_IDENTIFICADOR, " +
        "       COALESCE(TRIM(per.PRIMER_NOMBRE || ' ' || per.PRIMER_APELLIDO), usr.NOMBRE_USUARIO) AS REGISTRADO_POR_NOMBRE " +
        "FROM INCIDENTES i " +
        "LEFT JOIN UNIDADES u ON i.ID_UNIDAD = u.ID_UNIDAD " +
        "LEFT JOIN USUARIOS usr ON i.REGISTRADO_POR = usr.ID_USUARIO " +
        "LEFT JOIN PERSONAS per ON usr.ID_PERSONA = per.ID_PERSONA ";

    @Override
    public List<IncidenteDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = SELECT_BASE + "WHERE i.ID_PROPIEDAD = ? ORDER BY i.FECHA_HORA_INCIDENTE DESC";
        return jdbcTemplate.query(sql, rowMapper, idPropiedad);
    }

    @Override
    public List<IncidenteDTO> findAllByUnidad(Long idUnidad, Long idPropiedad) {
        String sql = SELECT_BASE + "WHERE i.ID_UNIDAD = ? AND i.ID_PROPIEDAD = ? ORDER BY i.FECHA_HORA_INCIDENTE DESC";
        return jdbcTemplate.query(sql, rowMapper, idUnidad, idPropiedad);
    }

    @Override
    public Optional<IncidenteDTO> findById(Long idIncidente, Long idPropiedad) {
        String sql = SELECT_BASE + "WHERE i.ID_INCIDENTE = ? AND i.ID_PROPIEDAD = ?";
        List<IncidenteDTO> list = jdbcTemplate.query(sql, rowMapper, idIncidente, idPropiedad);
        return list.stream().findFirst();
    }

    @Override
    public Long createIncidente(IncidenteDTO incidente, Long idPropiedad, Long registradoPor) {
        if (incidente.getIdUnidad() != null) {
            String checkSql = "SELECT COUNT(*) FROM UNIDADES WHERE ID_UNIDAD = ? AND ID_PROPIEDAD = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, incidente.getIdUnidad(), idPropiedad);
            if (count == null || count == 0) {
                throw new SecurityException("Unidad no pertenece a la propiedad actual.");
            }
        }

        String sql = "INSERT INTO INCIDENTES (ID_PROPIEDAD, ID_PORTERIA, ID_ZONA_COMUN, ID_UNIDAD, TITULO, " +
                     "TIPO_INCIDENTE, NIVEL_SEVERIDAD, DESCRIPCION_HECHOS, FECHA_HORA_INCIDENTE, REGISTRADO_POR, " +
                     "REQUIRIO_AUTORIDADES, ENTIDAD_AUTORIDAD, NUMERO_DENUNCIA_POLICIA, EVIDENCIAS_URLS, " +
                     "ACCIONES_INMEDIATAS, ESTADO) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_INCIDENTE"});
            ps.setLong(1, idPropiedad);
            
            if (incidente.getIdPorteria() != null) ps.setLong(2, incidente.getIdPorteria());
            else ps.setNull(2, Types.NUMERIC);
            
            if (incidente.getIdZonaComun() != null) ps.setLong(3, incidente.getIdZonaComun());
            else ps.setNull(3, Types.NUMERIC);
            
            if (incidente.getIdUnidad() != null) ps.setLong(4, incidente.getIdUnidad());
            else ps.setNull(4, Types.NUMERIC);
            
            ps.setString(5, incidente.getTitulo());
            ps.setString(6, incidente.getTipoIncidente());
            String sev = incidente.getNivelSeveridad() != null ? incidente.getNivelSeveridad().trim().toUpperCase() : "MODERADA";
            if ("MEDIA".equals(sev)) sev = "MODERADA";
            else if ("BAJA".equals(sev)) sev = "LEVE";
            else if ("ALTA".equals(sev)) sev = "GRAVE";
            else if (!List.of("LEVE", "MODERADA", "GRAVE", "CRITICA").contains(sev)) sev = "MODERADA";
            ps.setString(7, sev);
            ps.setString(8, incidente.getDescripcionHechos());
            ps.setTimestamp(9, Timestamp.from(incidente.getFechaHoraIncidente().toInstant()));
            ps.setLong(10, registradoPor);
            ps.setString(11, incidente.getRequirioAutoridades() != null ? incidente.getRequirioAutoridades() : "N");
            ps.setString(12, incidente.getEntidadAutoridad());
            ps.setString(13, incidente.getNumeroDenunciaPolicia());
            ps.setString(14, incidente.getEvidenciasUrls());
            ps.setString(15, incidente.getAccionesInmediatas());
            ps.setString(16, "REPORTADO");
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    @Override
    public void updateEstado(Long idIncidente, Long idPropiedad, String estado, String conclusiones) {
        String sql;
        int updated;
        if ("CERRADO".equals(estado)) {
            sql = "UPDATE INCIDENTES SET ESTADO = ?, CONCLUSIONES_CIERRE = ?, FECHA_CIERRE = SYSTIMESTAMP " +
                  "WHERE ID_INCIDENTE = ? AND ID_PROPIEDAD = ?";
            updated = jdbcTemplate.update(sql, estado, conclusiones, idIncidente, idPropiedad);
        } else {
            sql = "UPDATE INCIDENTES SET ESTADO = ?, CONCLUSIONES_CIERRE = COALESCE(?, CONCLUSIONES_CIERRE) " +
                  "WHERE ID_INCIDENTE = ? AND ID_PROPIEDAD = ?";
            updated = jdbcTemplate.update(sql, estado, conclusiones, idIncidente, idPropiedad);
        }
        if (updated == 0) {
            throw new IllegalArgumentException("Incidente no encontrado o sin acceso");
        }
    }

    @Override
    public void iniciarInvestigacion(Long idIncidente, Long idPropiedad, Long investigadoPor) {
        String sql = "UPDATE INCIDENTES SET ESTADO = 'EN_INVESTIGACION', INVESTIGADO_POR = ?, " +
                     "FECHA_INICIO_INVESTIGACION = SYSTIMESTAMP " +
                     "WHERE ID_INCIDENTE = ? AND ID_PROPIEDAD = ?";
        int updated = jdbcTemplate.update(sql, investigadoPor, idIncidente, idPropiedad);
        if (updated == 0) {
            throw new IllegalArgumentException("Incidente no encontrado o sin acceso");
        }
    }

    @Override
    public void actualizarInvestigacion(Long idIncidente, Long idPropiedad, String hallazgos) {
        String sql = "UPDATE INCIDENTES SET HALLAZGOS_INVESTIGACION = ? " +
                     "WHERE ID_INCIDENTE = ? AND ID_PROPIEDAD = ?";
        int updated = jdbcTemplate.update(sql, hallazgos, idIncidente, idPropiedad);
        if (updated == 0) {
            throw new IllegalArgumentException("Incidente no encontrado o sin acceso");
        }
    }

    @Override
    public void concluirInvestigacion(Long idIncidente, Long idPropiedad, String hallazgos, String estadoDestino) {
        String dest = (estadoDestino != null && !estadoDestino.isBlank()) ? estadoDestino : "ACCION_TOMADA";
        String sql = "UPDATE INCIDENTES SET ESTADO = ?, FECHA_FIN_INVESTIGACION = SYSTIMESTAMP, " +
                     "HALLAZGOS_INVESTIGACION = COALESCE(?, HALLAZGOS_INVESTIGACION) " +
                     "WHERE ID_INCIDENTE = ? AND ID_PROPIEDAD = ?";
        int updated = jdbcTemplate.update(sql, dest, hallazgos, idIncidente, idPropiedad);
        if (updated == 0) {
            throw new IllegalArgumentException("Incidente no encontrado o sin acceso");
        }
    }

    @Override
    public void escalarIncidente(Long idIncidente, Long idPropiedad, Long escaladoPor, String motivo, String sancionSugerida) {
        String sql = "UPDATE INCIDENTES SET ESTADO = 'ESCALADO_A_SANCION', ESCALADO_POR = ?, " +
                     "FECHA_ESCALAMIENTO = SYSTIMESTAMP, MOTIVO_ESCALAMIENTO = ?, SANCION_SUGERIDA = ? " +
                     "WHERE ID_INCIDENTE = ? AND ID_PROPIEDAD = ?";
        int updated = jdbcTemplate.update(sql, escaladoPor, motivo, sancionSugerida, idIncidente, idPropiedad);
        if (updated == 0) {
            throw new IllegalArgumentException("Incidente no encontrado o sin acceso");
        }
    }

    @Override
    public List<IncidenteInvolucradoDTO> findInvolucradosByIncidente(Long idIncidente) {
        String sql = """
            SELECT inv.*,
                   TRIM(p.PRIMER_NOMBRE || ' ' || NVL(p.SEGUNDO_NOMBRE, '') || ' ' || p.PRIMER_APELLIDO || ' ' || NVL(p.SEGUNDO_APELLIDO, '')) AS NOMBRE_PERSONA,
                   p.NUMERO_DOCUMENTO AS DOCUMENTO_PERSONA,
                   v.PLACA AS PLACA_VEHICULO
            FROM INCIDENTE_INVOLUCRADOS inv
            LEFT JOIN PERSONAS p ON inv.ID_PERSONA = p.ID_PERSONA
            LEFT JOIN VEHICULOS v ON inv.ID_VEHICULO = v.ID_VEHICULO
            WHERE inv.ID_INCIDENTE = ?
            ORDER BY inv.ID_INCIDENTE_INVOLUCRADO ASC
        """;
        return jdbcTemplate.query(sql, involucradoRowMapper, idIncidente);
    }

    @Override
    public Long addInvolucrado(Long idIncidente, IncidenteInvolucradoDTO dto) {
        String sql = "INSERT INTO INCIDENTE_INVOLUCRADOS (ID_INCIDENTE, ID_PERSONA, ID_VEHICULO, " +
                     "NOMBRE_IDENTIFICACION_EXTERNA, ROL_EN_INCIDENTE, DECLARACION_TESTIMONIO) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_INCIDENTE_INVOLUCRADO"});
            ps.setLong(1, idIncidente);
            if (dto.getIdPersona() != null) ps.setLong(2, dto.getIdPersona());
            else ps.setNull(2, Types.NUMERIC);

            if (dto.getIdVehiculo() != null) ps.setLong(3, dto.getIdVehiculo());
            else ps.setNull(3, Types.NUMERIC);

            ps.setString(4, dto.getNombreIdentificacionExterna());
            ps.setString(5, dto.getRolEnIncidente());
            ps.setString(6, dto.getDeclaracionTestimonio());
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    @Override
    public void removeInvolucrado(Long idIncidente, Long idInvolucrado) {
        String sql = "DELETE FROM INCIDENTE_INVOLUCRADOS WHERE ID_INCIDENTE = ? AND ID_INCIDENTE_INVOLUCRADO = ?";
        int deleted = jdbcTemplate.update(sql, idIncidente, idInvolucrado);
        if (deleted == 0) {
            throw new IllegalArgumentException("Involucrado no encontrado en el incidente indicado.");
        }
    }

    @Override
    public boolean isPersonaInPropiedad(Long idPersona, Long idPropiedad) {
        if (idPersona == null || idPropiedad == null) return false;
        String sql = """
            SELECT COUNT(1) FROM (
                SELECT ru.ID_PERSONA FROM RESIDENTES_UNIDAD ru
                JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD
                WHERE u.ID_PROPIEDAD = ? AND ru.ID_PERSONA = ?
                UNION ALL
                SELECT pu.ID_PERSONA FROM PROPIETARIOS_UNIDAD pu
                JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD
                WHERE u.ID_PROPIEDAD = ? AND pu.ID_PERSONA = ?
                UNION ALL
                SELECT vis.ID_PERSONA FROM VISITANTES vis
                JOIN VISITAS v ON vis.ID_VISITANTE = v.ID_VISITANTE
                JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD
                WHERE u.ID_PROPIEDAD = ? AND vis.ID_PERSONA = ?
                UNION ALL
                SELECT u.ID_PERSONA FROM USUARIOS u
                JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO
                WHERE (ua.ID_PROPIEDAD = ? OR ua.ID_ORGANIZACION = (SELECT ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = ?))
                  AND u.ID_PERSONA = ?
                UNION ALL
                SELECT t.ID_PERSONA FROM TRABAJADORES t
                JOIN PROVEEDORES prov ON t.ID_PROVEEDOR = prov.ID_PROVEEDOR
                WHERE prov.ID_ORGANIZACION = (SELECT ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = ?)
                  AND t.ID_PERSONA = ?
            )
        """;
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                idPropiedad, idPersona,
                idPropiedad, idPersona,
                idPropiedad, idPersona,
                idPropiedad, idPropiedad, idPersona,
                idPropiedad, idPersona);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
