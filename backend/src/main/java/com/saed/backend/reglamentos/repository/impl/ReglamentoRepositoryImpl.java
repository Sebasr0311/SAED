package com.saed.backend.reglamentos.repository.impl;

import com.saed.backend.reglamentos.dto.ReglamentoDTO;
import com.saed.backend.reglamentos.repository.ReglamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ReglamentoRepositoryImpl implements ReglamentoRepository {

    private static final Logger log = LoggerFactory.getLogger(ReglamentoRepositoryImpl.class);
    private final JdbcTemplate jdbcTemplate;

    public ReglamentoRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String BASE_SELECT =
        "SELECT R.ID_REGLAMENTO, R.ID_ORGANIZACION, R.ID_PROPIEDAD, R.TIPO_NORMATIVA, R.TITULO, " +
        "       R.DESCRIPCION, R.ID_DOCUMENTO, R.ESTADO, R.FECHA_ENTRADA_EN_VIGOR, R.FECHA_PUBLICACION, " +
        "       R.CREADO_POR, R.PUBLICADO_POR, R.FECHA_CREACION, R.FECHA_MODIFICACION, " +
        "       P.NOMBRE AS NOMBRE_PROPIEDAD, " +
        "       V.ARCHIVO_NOMBRE_ORIG, V.ARCHIVO_TAMANO_BYTES, V.ARCHIVO_MIME_TYPE, V.ARCHIVO_SHA256, V.NUMERO_VERSION " +
        "FROM REGLAMENTOS_NORMATIVA R " +
        "LEFT JOIN PROPIEDADES P ON R.ID_PROPIEDAD = P.ID_PROPIEDAD " +
        "LEFT JOIN DOCUMENTOS D ON R.ID_DOCUMENTO = D.ID_DOCUMENTO " +
        "LEFT JOIN (SELECT * FROM VERSIONES_DOCUMENTO WHERE (ID_DOCUMENTO, NUMERO_VERSION) IN " +
        "           (SELECT ID_DOCUMENTO, MAX(NUMERO_VERSION) FROM VERSIONES_DOCUMENTO GROUP BY ID_DOCUMENTO)) V " +
        "ON D.ID_DOCUMENTO = V.ID_DOCUMENTO";

    private final RowMapper<ReglamentoDTO> rowMapper = (rs, rowNum) -> {
        ReglamentoDTO dto = new ReglamentoDTO();
        dto.setIdReglamento(rs.getLong("ID_REGLAMENTO"));
        dto.setIdOrganizacion(rs.getLong("ID_ORGANIZACION"));
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        dto.setTipoNormativa(rs.getString("TIPO_NORMATIVA"));
        dto.setTitulo(rs.getString("TITULO"));
        dto.setDescripcion(rs.getString("DESCRIPCION"));
        dto.setIdDocumento(rs.getLong("ID_DOCUMENTO"));
        dto.setEstado(rs.getString("ESTADO"));

        Date fVigor = rs.getDate("FECHA_ENTRADA_EN_VIGOR");
        if (fVigor != null) dto.setFechaEntradaEnVigor(fVigor.toLocalDate());

        Timestamp fPub = rs.getTimestamp("FECHA_PUBLICACION");
        if (fPub != null) dto.setFechaPublicacion(fPub.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));

        long creador = rs.getLong("CREADO_POR");
        if (!rs.wasNull()) dto.setCreadoPor(creador);

        long pub = rs.getLong("PUBLICADO_POR");
        if (!rs.wasNull()) dto.setPublicadoPor(pub);

        Timestamp fc = rs.getTimestamp("FECHA_CREACION");
        if (fc != null) dto.setFechaCreacion(fc.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));

        Timestamp fm = rs.getTimestamp("FECHA_MODIFICACION");
        if (fm != null) dto.setFechaModificacion(fm.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));

        dto.setNombrePropiedad(rs.getString("NOMBRE_PROPIEDAD"));
        dto.setArchivoNombreOrig(rs.getString("ARCHIVO_NOMBRE_ORIG"));

        long tamano = rs.getLong("ARCHIVO_TAMANO_BYTES");
        if (!rs.wasNull()) dto.setArchivoTamanoBytes(tamano);

        dto.setArchivoMimeType(rs.getString("ARCHIVO_MIME_TYPE"));
        dto.setArchivoSha256(rs.getString("ARCHIVO_SHA256"));

        int version = rs.getInt("NUMERO_VERSION");
        if (!rs.wasNull()) dto.setNumeroVersion(version);

        return dto;
    };

    @Override
    public Long create(ReglamentoDTO dto) {
        String sql = "INSERT INTO REGLAMENTOS_NORMATIVA (" +
                     "ID_ORGANIZACION, ID_PROPIEDAD, TIPO_NORMATIVA, TITULO, DESCRIPCION, " +
                     "ID_DOCUMENTO, ESTADO, CREADO_POR, FECHA_CREACION" +
                     ") VALUES (?, ?, ?, ?, ?, ?, 'BORRADOR', ?, CURRENT_TIMESTAMP)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_REGLAMENTO"});
            ps.setLong(1, dto.getIdOrganizacion());
            ps.setLong(2, dto.getIdPropiedad());
            ps.setString(3, dto.getTipoNormativa());
            ps.setString(4, dto.getTitulo());
            ps.setString(5, dto.getDescripcion());
            ps.setLong(6, dto.getIdDocumento());
            if (dto.getCreadoPor() != null) ps.setLong(7, dto.getCreadoPor());
            else ps.setNull(7, java.sql.Types.NUMERIC);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated ID for REGLAMENTOS_NORMATIVA");
        }
        return key.longValue();
    }

    @Override
    public void update(ReglamentoDTO dto) {
        String sql = "UPDATE REGLAMENTOS_NORMATIVA SET " +
                     "TIPO_NORMATIVA = ?, TITULO = ?, DESCRIPCION = ?, ID_DOCUMENTO = ?, FECHA_MODIFICACION = CURRENT_TIMESTAMP " +
                     "WHERE ID_REGLAMENTO = ? AND ESTADO = 'BORRADOR'";
        jdbcTemplate.update(sql, dto.getTipoNormativa(), dto.getTitulo(), dto.getDescripcion(), dto.getIdDocumento(), dto.getIdReglamento());
    }

    @Override
    public Optional<ReglamentoDTO> findById(Long idReglamento) {
        String sql = BASE_SELECT + " WHERE R.ID_REGLAMENTO = ?";
        List<ReglamentoDTO> list = jdbcTemplate.query(sql, rowMapper, idReglamento);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<ReglamentoDTO> findVigenteByPropiedadAndTipo(Long idPropiedad, String tipoNormativa) {
        String sql = BASE_SELECT + " WHERE R.ID_PROPIEDAD = ? AND R.TIPO_NORMATIVA = ? AND R.ESTADO = 'PUBLICADO'";
        List<ReglamentoDTO> list = jdbcTemplate.query(sql, rowMapper, idPropiedad, tipoNormativa);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<ReglamentoDTO> findAllAdmin(Long idPropiedad, Long idOrganizacion, String tipoNormativa, String estado) {
        StringBuilder sb = new StringBuilder(BASE_SELECT);
        sb.append(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (idPropiedad != null) {
            sb.append(" AND R.ID_PROPIEDAD = ?");
            params.add(idPropiedad);
        } else if (idOrganizacion != null) {
            sb.append(" AND R.ID_ORGANIZACION = ?");
            params.add(idOrganizacion);
        }
        if (tipoNormativa != null && !tipoNormativa.isBlank()) {
            sb.append(" AND R.TIPO_NORMATIVA = ?");
            params.add(tipoNormativa);
        }
        if (estado != null && !estado.isBlank()) {
            sb.append(" AND R.ESTADO = ?");
            params.add(estado);
        }
        sb.append(" ORDER BY R.FECHA_CREACION DESC");
        return jdbcTemplate.query(sb.toString(), rowMapper, params.toArray());
    }

    @Override
    public List<ReglamentoDTO> findAllResidente(Long idPropiedad, String tipoNormativa) {
        StringBuilder sb = new StringBuilder(BASE_SELECT);
        sb.append(" WHERE R.ID_PROPIEDAD = ? AND R.ESTADO = 'PUBLICADO'");
        List<Object> params = new ArrayList<>();
        params.add(idPropiedad);
        if (tipoNormativa != null && !tipoNormativa.isBlank()) {
            sb.append(" AND R.TIPO_NORMATIVA = ?");
            params.add(tipoNormativa);
        }
        sb.append(" ORDER BY R.TIPO_NORMATIVA ASC, R.FECHA_PUBLICACION DESC");
        return jdbcTemplate.query(sb.toString(), rowMapper, params.toArray());
    }

    @Override
    public void lockPropiedadForUpdate(Long idPropiedad) {
        String sql = "SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ID_PROPIEDAD = ? FOR UPDATE";
        jdbcTemplate.queryForObject(sql, Long.class, idPropiedad);
    }

    @Override
    public Optional<Long> lockVigenteForUpdate(Long idPropiedad, String tipoNormativa) {
        String sql = "SELECT ID_REGLAMENTO FROM REGLAMENTOS_NORMATIVA " +
                     "WHERE ID_PROPIEDAD = ? AND TIPO_NORMATIVA = ? AND ESTADO = 'PUBLICADO' FOR UPDATE";
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("ID_REGLAMENTO"), idPropiedad, tipoNormativa);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    @Override
    public void reemplazar(Long idReglamento, Long idUsuario) {
        String sql = "UPDATE REGLAMENTOS_NORMATIVA SET ESTADO = 'REEMPLAZADO', FECHA_MODIFICACION = CURRENT_TIMESTAMP WHERE ID_REGLAMENTO = ?";
        jdbcTemplate.update(sql, idReglamento);
    }

    @Override
    public void publicar(Long idReglamento, LocalDate fechaVigor, Long idUsuario) {
        String sql = "UPDATE REGLAMENTOS_NORMATIVA SET ESTADO = 'PUBLICADO', " +
                     "FECHA_ENTRADA_EN_VIGOR = ?, FECHA_PUBLICACION = CURRENT_TIMESTAMP, " +
                     "PUBLICADO_POR = ?, FECHA_MODIFICACION = CURRENT_TIMESTAMP WHERE ID_REGLAMENTO = ?";
        Date sqlDate = fechaVigor != null ? Date.valueOf(fechaVigor) : Date.valueOf(LocalDate.now());
        jdbcTemplate.update(sql, sqlDate, idUsuario, idReglamento);
    }

    @Override
    public void inactivar(Long idReglamento, Long idUsuario) {
        String sql = "UPDATE REGLAMENTOS_NORMATIVA SET ESTADO = 'INACTIVO', FECHA_MODIFICACION = CURRENT_TIMESTAMP WHERE ID_REGLAMENTO = ?";
        jdbcTemplate.update(sql, idReglamento);
    }

    @Override
    public boolean existsDocumentoInPropiedad(Long idDocumento, Long idPropiedad, Long idOrganizacion) {
        String sql = "SELECT COUNT(1) FROM DOCUMENTOS WHERE ID_DOCUMENTO = ? AND ESTADO <> 'ELIMINADO' " +
                     "AND (ID_PROPIEDAD = ? OR (ID_PROPIEDAD IS NULL AND ID_ORGANIZACION = ?))";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, idDocumento, idPropiedad, idOrganizacion);
        return count != null && count > 0;
    }

    @Override
    public void markDocumentoPublicoResidentes(Long idDocumento) {
        String sql = "UPDATE DOCUMENTOS SET ES_PUBLICO_RESIDENTES = 'S' WHERE ID_DOCUMENTO = ?";
        jdbcTemplate.update(sql, idDocumento);
    }
}
