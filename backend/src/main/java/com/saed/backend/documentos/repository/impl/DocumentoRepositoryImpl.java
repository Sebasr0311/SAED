package com.saed.backend.documentos.repository.impl;

import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.repository.DocumentoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;

@Repository
public class DocumentoRepositoryImpl implements DocumentoRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentoRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DocumentoDTO> rowMapper = (rs, rowNum) -> {
        DocumentoDTO dto = new DocumentoDTO();
        dto.setIdDocumento(rs.getLong("ID_DOCUMENTO"));
        dto.setIdOrganizacion(rs.getLong("ID_ORGANIZACION"));
        
        long idProp = rs.getLong("ID_PROPIEDAD");
        if (!rs.wasNull()) dto.setIdPropiedad(idProp);
        
        long idUnid = rs.getLong("ID_UNIDAD");
        if (!rs.wasNull()) dto.setIdUnidad(idUnid);

        dto.setCategoria(rs.getString("CATEGORIA"));
        dto.setTitulo(rs.getString("TITULO"));
        dto.setDescripcion(rs.getString("DESCRIPCION"));
        dto.setEsPublicoResidentes(rs.getString("ES_PUBLICO_RESIDENTES"));
        dto.setRolMinimoAcceso(rs.getString("ROL_MINIMO_ACCESO"));
        dto.setEstado(rs.getString("ESTADO"));
        dto.setCreadoPor(rs.getLong("CREADO_POR"));

        Timestamp fc = rs.getTimestamp("FECHA_CREACION");
        if (fc != null) dto.setFechaCreacion(fc.toLocalDateTime().atZone(ZoneId.of("America/Bogota")));

        // Version data
        dto.setArchivoUrl(rs.getString("ARCHIVO_URL"));
        dto.setArchivoNombreOrig(rs.getString("ARCHIVO_NOMBRE_ORIG"));
        
        long tamano = rs.getLong("ARCHIVO_TAMANO_BYTES");
        if (!rs.wasNull()) dto.setArchivoTamanoBytes(tamano);
        
        dto.setArchivoMimeType(rs.getString("ARCHIVO_MIME_TYPE"));
        
        int version = rs.getInt("NUMERO_VERSION");
        if (!rs.wasNull()) dto.setNumeroVersion(version);

        return dto;
    };

    @Override
    public List<DocumentoDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = "SELECT D.*, V.ARCHIVO_URL, V.ARCHIVO_NOMBRE_ORIG, V.ARCHIVO_TAMANO_BYTES, V.ARCHIVO_MIME_TYPE, V.NUMERO_VERSION " +
                     "FROM DOCUMENTOS D " +
                     "LEFT JOIN (SELECT * FROM VERSIONES_DOCUMENTO WHERE (ID_DOCUMENTO, NUMERO_VERSION) IN " +
                     "(SELECT ID_DOCUMENTO, MAX(NUMERO_VERSION) FROM VERSIONES_DOCUMENTO GROUP BY ID_DOCUMENTO)) V " +
                     "ON D.ID_DOCUMENTO = V.ID_DOCUMENTO " +
                     "WHERE D.ID_PROPIEDAD = ? OR D.ID_PROPIEDAD IS NULL " +
                     "ORDER BY D.FECHA_CREACION DESC";
        return jdbcTemplate.query(sql, rowMapper, idPropiedad);
    }

    @Override
    public List<DocumentoDTO> findPublicosByPropiedad(Long idPropiedad) {
        String sql = "SELECT D.*, V.ARCHIVO_URL, V.ARCHIVO_NOMBRE_ORIG, V.ARCHIVO_TAMANO_BYTES, V.ARCHIVO_MIME_TYPE, V.NUMERO_VERSION " +
                     "FROM DOCUMENTOS D " +
                     "LEFT JOIN (SELECT * FROM VERSIONES_DOCUMENTO WHERE (ID_DOCUMENTO, NUMERO_VERSION) IN " +
                     "(SELECT ID_DOCUMENTO, MAX(NUMERO_VERSION) FROM VERSIONES_DOCUMENTO GROUP BY ID_DOCUMENTO)) V " +
                     "ON D.ID_DOCUMENTO = V.ID_DOCUMENTO " +
                     "WHERE (D.ID_PROPIEDAD = ? OR D.ID_PROPIEDAD IS NULL) " +
                     "AND D.ES_PUBLICO_RESIDENTES = 'S' AND D.ESTADO = 'ACTIVO' " +
                     "ORDER BY D.FECHA_CREACION DESC";
        return jdbcTemplate.query(sql, rowMapper, idPropiedad);
    }

    @Override
    public Long createDocumento(DocumentoDTO documento, Long idOrganizacion, Long idPropiedad, Long creadoPor) {
        String sql = "INSERT INTO DOCUMENTOS (ID_ORGANIZACION, ID_PROPIEDAD, CATEGORIA, TITULO, DESCRIPCION, ES_PUBLICO_RESIDENTES, ROL_MINIMO_ACCESO, CREADO_POR) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_DOCUMENTO"});
            ps.setLong(1, idOrganizacion);
            ps.setLong(2, idPropiedad);
            ps.setString(3, documento.getCategoria());
            ps.setString(4, documento.getTitulo());
            ps.setString(5, documento.getDescripcion());
            ps.setString(6, documento.getEsPublicoResidentes() != null ? documento.getEsPublicoResidentes() : "N");
            ps.setString(7, documento.getRolMinimoAcceso() != null ? documento.getRolMinimoAcceso() : "ADMIN_PROPIEDAD");
            ps.setLong(8, creadoPor);
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    @Override
    public void addVersion(Long idDocumento, DocumentoDTO doc, Long subidoPor) {
        String sql = "INSERT INTO VERSIONES_DOCUMENTO (ID_DOCUMENTO, NUMERO_VERSION, ARCHIVO_URL, ARCHIVO_NOMBRE_ORIG, ARCHIVO_TAMANO_BYTES, ARCHIVO_MIME_TYPE, SUBIDO_POR) " +
                     "VALUES (?, (SELECT NVL(MAX(NUMERO_VERSION), 0) + 1 FROM VERSIONES_DOCUMENTO WHERE ID_DOCUMENTO = ?), ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, idDocumento, idDocumento, doc.getArchivoUrl(), doc.getArchivoNombreOrig(), doc.getArchivoTamanoBytes(), doc.getArchivoMimeType(), subidoPor);
    }

    @Override
    public void deleteDocumento(Long idDocumento, Long idPropiedad) {
        // Enforce tenant isolation strictly
        String sql = "DELETE FROM DOCUMENTOS WHERE ID_DOCUMENTO = ? AND ID_PROPIEDAD = ?";
        jdbcTemplate.update(sql, idDocumento, idPropiedad);
    }
}
