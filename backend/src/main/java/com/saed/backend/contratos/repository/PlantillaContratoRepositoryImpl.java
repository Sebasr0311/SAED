package com.saed.backend.contratos.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import com.saed.backend.contratos.dto.PlantillaContratoRequestDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Repository
public class PlantillaContratoRepositoryImpl implements PlantillaContratoRepository {

    private static final Logger log = LoggerFactory.getLogger(PlantillaContratoRepositoryImpl.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PlantillaContratoRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void ensureTableExists() {
        try {
            Integer count = jdbcTemplate.getJdbcTemplate().queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'PLANTILLAS_CONTRATOS'",
                Integer.class
            );
            if (count == null || count == 0) {
                log.info("[PlantillasContratos] Creando tabla PLANTILLAS_CONTRATOS...");
                jdbcTemplate.getJdbcTemplate().execute("""
                    CREATE TABLE PLANTILLAS_CONTRATOS (
                        ID_PLANTILLA NUMBER GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER NOCYCLE NOT NULL ENABLE,
                        ID_ORGANIZACION NUMBER NOT NULL ENABLE,
                        CODIGO VARCHAR2(50 CHAR) NOT NULL ENABLE,
                        NOMBRE VARCHAR2(150 CHAR) NOT NULL ENABLE,
                        TIPO_CONTRATO VARCHAR2(50 CHAR) NOT NULL ENABLE,
                        DESCRIPCION VARCHAR2(500 CHAR),
                        CONTENIDO_HTML CLOB NOT NULL ENABLE,
                        VARIABLES_DISPONIBLES CLOB,
                        CAMPOS_REQUERIDOS CLOB,
                        VERSION NUMBER(5, 0) DEFAULT 1 NOT NULL ENABLE,
                        ESTADO VARCHAR2(30 CHAR) DEFAULT 'ACTIVA' NOT NULL ENABLE,
                        VIGENCIA_DESDE DATE DEFAULT CURRENT_DATE NOT NULL ENABLE,
                        VIGENCIA_HASTA DATE,
                        CREADO_POR NUMBER,
                        FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ENABLE,
                        FECHA_ACTUALIZACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ENABLE,
                        CONSTRAINT PK_PLANTILLAS_CONTRATOS PRIMARY KEY (ID_PLANTILLA),
                        CONSTRAINT FK_PLANTILLAS_CONTRATOS_ORG FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION) ON DELETE CASCADE,
                        CONSTRAINT UQ_PLANTILLAS_ORG_COD_VER UNIQUE (ID_ORGANIZACION, CODIGO, VERSION),
                        CONSTRAINT CK_PLANTILLAS_CONTR_TIPO CHECK (TIPO_CONTRATO IN ('INICIAL', 'RENOVACION', 'PERMANENCIA', 'COMERCIAL', 'OTRO')),
                        CONSTRAINT CK_PLANTILLAS_CONTR_ESTADO CHECK (ESTADO IN ('ACTIVA', 'BORRADOR', 'HISTORICA'))
                    )
                """);
                try {
                    jdbcTemplate.getJdbcTemplate().execute("CREATE INDEX IX_PLANTILLAS_ORG_ESTADO ON PLANTILLAS_CONTRATOS (ID_ORGANIZACION, ESTADO)");
                    jdbcTemplate.getJdbcTemplate().execute("CREATE INDEX IX_PLANTILLAS_ORG_TIPO ON PLANTILLAS_CONTRATOS (ID_ORGANIZACION, TIPO_CONTRATO)");
                } catch (Exception ignored) {}

                try {
                    jdbcTemplate.getJdbcTemplate().execute("""
                        BEGIN
                            DBMS_RLS.ADD_GROUPED_POLICY(
                                object_schema   => NULL,
                                object_name     => 'PLANTILLAS_CONTRATOS',
                                policy_group    => 'SYS_DEFAULT',
                                policy_name     => 'POL_RLS_ORG_PLANTILLAS_CONTR',
                                function_schema => NULL,
                                policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION',
                                statement_types => 'SELECT,INSERT,UPDATE,DELETE',
                                update_check    => TRUE,
                                enable          => TRUE,
                                static_policy   => FALSE,
                                policy_type     => DBMS_RLS.DYNAMIC
                            );
                        EXCEPTION
                            WHEN OTHERS THEN
                                NULL;
                        END;
                    """);
                } catch (Exception e) {
                    log.debug("[PlantillasContratos] Aviso RLS en PLANTILLAS_CONTRATOS: {}", e.getMessage());
                }
                log.info("[PlantillasContratos] Tabla PLANTILLAS_CONTRATOS creada exitosamente.");
            }

            seedDefaultTemplateIfEmpty(1L);
        } catch (Exception ex) {
            log.warn("[PlantillasContratos] Aviso al verificar o inicializar tabla: {}", ex.getMessage());
        }
    }

    public void seedDefaultTemplateIfEmpty(Long orgId) {
        if (orgId == null) return;
        try {
            Integer countTemplates = jdbcTemplate.getJdbcTemplate().queryForObject(
                "SELECT COUNT(1) FROM PLANTILLAS_CONTRATOS WHERE ID_ORGANIZACION = ?",
                Integer.class,
                orgId
            );
            if (countTemplates == null || countTemplates == 0) {
                String defaultHtml = """
                    <h2>CONTRATO DE ARRENDAMIENTO DE VIVIENDA URBANA</h2>
                    <p>Entre los suscritos a saber, <strong>${propiedad.nombre}</strong> (en adelante EL ARRENDADOR), ubicada en ${propiedad.direccion}, ${propiedad.ciudad}, y por la otra parte <strong>${inquilino.nombre_completo}</strong>, identificado con ${inquilino.tipo_documento} No. ${inquilino.numero_documento} (en adelante EL ARRENDATARIO), se ha celebrado el presente contrato sobre el inmueble:</p>
                    <ul>
                      <li><strong>Unidad:</strong> Apartamento ${apartamento.numero} ${apartamento.bloque}</li>
                      <li><strong>Canon Mensual:</strong> $${contrato.canon_mensual} COP</li>
                      <li><strong>Fecha de Inicio:</strong> ${contrato.fecha_inicio}</li>
                      <li><strong>Fecha de Terminación:</strong> ${contrato.fecha_fin}</li>
                    </ul>
                    <p>El arrendatario se compromete al cumplimiento cabal de las normas de convivencia de la copropiedad y al pago oportuno en los primeros cinco (5) días de cada mes calendario.</p>
                    <p>En constancia se firma en la fecha: ${fecha_actual}.</p>
                """;
                try {
                    jdbcTemplate.getJdbcTemplate().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, " + orgId + ", 1, 'SUPERADMIN'); END;");
                } catch (Exception ignored) {}

                jdbcTemplate.getJdbcTemplate().update("""
                    INSERT INTO PLANTILLAS_CONTRATOS (
                        ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, DESCRIPCION,
                        CONTENIDO_HTML, VARIABLES_DISPONIBLES, CAMPOS_REQUERIDOS, VERSION,
                        ESTADO, VIGENCIA_DESDE, CREADO_POR
                    ) VALUES (
                        ?, 'CONTRATO_ESTANDAR_2026', 'Contrato Estándar Residencial', 'INICIAL',
                        'Plantilla base predeterminada para contratos de arrendamiento residencial.',
                        ?, '["propiedad.nombre","inquilino.nombre_completo","apartamento.numero","contrato.canon_mensual"]',
                        '["propiedad.nombre","inquilino.nombre_completo","apartamento.numero"]', 1,
                        'ACTIVA', TRUNC(SYSDATE), 1
                    )
                """, orgId, defaultHtml);
                log.info("[PlantillasContratos] Plantilla de contrato inicial sembrada para organizacion {}.", orgId);
            }
        } catch (Exception e) {
            log.debug("[PlantillasContratos] Aviso al sembrar plantilla inicial para org {}: {}", orgId, e.getMessage());
        }
    }

    private Long extractGeneratedKey(KeyHolder kh, String columnName) {
        if (kh == null) return null;
        if (kh.getKey() != null) return kh.getKey().longValue();
        if (kh.getKeys() != null) {
            for (Map.Entry<String, Object> entry : kh.getKeys().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(columnName) && entry.getValue() instanceof Number num) {
                    return num.longValue();
                }
            }
        }
        if (kh.getKeyList() != null) {
            for (Map<String, Object> map : kh.getKeyList()) {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(columnName) && entry.getValue() instanceof Number num) {
                        return num.longValue();
                    }
                }
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (!entry.getKey().equalsIgnoreCase("ROWID") && entry.getValue() instanceof Number num) {
                        return num.longValue();
                    }
                }
            }
        }
        return null;
    }

    private PlantillaContratoDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        PlantillaContratoDTO dto = new PlantillaContratoDTO();
        dto.setIdPlantilla(rs.getLong("id_plantilla"));
        dto.setIdOrganizacion(rs.getLong("id_organizacion"));
        dto.setCodigo(rs.getString("codigo"));
        dto.setNombre(rs.getString("nombre"));
        dto.setTipoContrato(rs.getString("tipo_contrato"));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setContenidoHtml(rs.getString("contenido_html"));

        String varsJson = rs.getString("variables_disponibles");
        if (varsJson != null && !varsJson.isBlank()) {
            try {
                dto.setVariablesDisponibles(objectMapper.readValue(varsJson, new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                dto.setVariablesDisponibles(Collections.emptyList());
            }
        } else {
            dto.setVariablesDisponibles(Collections.emptyList());
        }

        String reqJson = rs.getString("campos_requeridos");
        if (reqJson != null && !reqJson.isBlank()) {
            try {
                dto.setCamposRequeridos(objectMapper.readValue(reqJson, new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                dto.setCamposRequeridos(Collections.emptyList());
            }
        } else {
            dto.setCamposRequeridos(Collections.emptyList());
        }

        dto.setVersion(rs.getInt("version"));
        dto.setEstado(rs.getString("estado"));

        java.sql.Date vd = rs.getDate("vigencia_desde");
        if (vd != null) dto.setVigenciaDesde(vd.toLocalDate());

        java.sql.Date vh = rs.getDate("vigencia_hasta");
        if (vh != null) dto.setVigenciaHasta(vh.toLocalDate());

        long cp = rs.getLong("creado_por");
        if (!rs.wasNull()) dto.setCreadoPor(cp);

        try {
            java.sql.Timestamp fc = rs.getTimestamp("fecha_creacion");
            if (fc != null) dto.setFechaCreacion(fc.toInstant().atOffset(java.time.ZoneOffset.UTC));
        } catch (Exception ignored) {}

        try {
            java.sql.Timestamp fa = rs.getTimestamp("fecha_actualizacion");
            if (fa != null) dto.setFechaActualizacion(fa.toInstant().atOffset(java.time.ZoneOffset.UTC));
        } catch (Exception ignored) {}

        return dto;
    }

    @Override
    public List<PlantillaContratoDTO> findByOrganizacionId(Long orgId, String estado) {
        StringBuilder sql = new StringBuilder("""
            SELECT id_plantilla, id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                   contenido_html, variables_disponibles, campos_requeridos, version, estado,
                   vigencia_desde, vigencia_hasta, creado_por, fecha_creacion, fecha_actualizacion
            FROM PLANTILLAS_CONTRATOS
            WHERE id_organizacion = :orgId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND estado = :estado");
            params.addValue("estado", estado.toUpperCase());
        }
        sql.append(" ORDER BY tipo_contrato, nombre, version DESC");

        try {
            List<PlantillaContratoDTO> results = jdbcTemplate.query(sql.toString(), params, this::mapRow);
            if (results.isEmpty()) {
                seedDefaultTemplateIfEmpty(orgId);
                results = jdbcTemplate.query(sql.toString(), params, this::mapRow);
            }
            return results;
        } catch (DataAccessException dae) {
            log.warn("[PlantillasContratos] Excepción al consultar para org {}. Intentando asegurar esquema: {}", orgId, dae.getMessage());
            try {
                ensureTableExists();
                seedDefaultTemplateIfEmpty(orgId);
                return jdbcTemplate.query(sql.toString(), params, this::mapRow);
            } catch (Exception retryEx) {
                log.error("[PlantillasContratos] Fallo reintentando consulta para org {}: {}", orgId, retryEx.getMessage());
                return Collections.emptyList();
            }
        }
    }

    @Override
    public List<PlantillaContratoDTO> findActivasByOrganizacionId(Long orgId) {
        String sql = """
            SELECT id_plantilla, id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                   contenido_html, variables_disponibles, campos_requeridos, version, estado,
                   vigencia_desde, vigencia_hasta, creado_por, fecha_creacion, fecha_actualizacion
            FROM PLANTILLAS_CONTRATOS
            WHERE id_organizacion = :orgId
              AND estado = 'ACTIVA'
              AND (vigencia_hasta IS NULL OR vigencia_hasta >= CURRENT_DATE)
            ORDER BY nombre
        """;
        try {
            List<PlantillaContratoDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("orgId", orgId), this::mapRow);
            if (results.isEmpty()) {
                seedDefaultTemplateIfEmpty(orgId);
                results = jdbcTemplate.query(sql, new MapSqlParameterSource("orgId", orgId), this::mapRow);
            }
            return results;
        } catch (DataAccessException dae) {
            log.warn("[PlantillasContratos] Excepción al consultar activas para org {}: {}", orgId, dae.getMessage());
            try {
                ensureTableExists();
                seedDefaultTemplateIfEmpty(orgId);
                return jdbcTemplate.query(sql, new MapSqlParameterSource("orgId", orgId), this::mapRow);
            } catch (Exception retryEx) {
                log.error("[PlantillasContratos] Fallo reintentando consulta activas: {}", retryEx.getMessage());
                return Collections.emptyList();
            }
        }
    }

    @Override
    public Optional<PlantillaContratoDTO> findById(Long id) {
        String sql = """
            SELECT id_plantilla, id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                   contenido_html, variables_disponibles, campos_requeridos, version, estado,
                   vigencia_desde, vigencia_hasta, creado_por, fecha_creacion, fecha_actualizacion
            FROM PLANTILLAS_CONTRATOS
            WHERE id_plantilla = :id
        """;
        try {
            List<PlantillaContratoDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), this::mapRow);
            return list.stream().findFirst();
        } catch (DataAccessException dae) {
            log.warn("[PlantillasContratos] Excepción al consultar por id {}: {}", id, dae.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<PlantillaContratoDTO> findByCodigoAndVersion(Long orgId, String codigo, Integer version) {
        String sql = """
            SELECT id_plantilla, id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                   contenido_html, variables_disponibles, campos_requeridos, version, estado,
                   vigencia_desde, vigencia_hasta, creado_por, fecha_creacion, fecha_actualizacion
            FROM PLANTILLAS_CONTRATOS
            WHERE id_organizacion = :orgId AND codigo = :codigo AND version = :version
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("codigo", codigo)
                .addValue("version", version);
        try {
            List<PlantillaContratoDTO> list = jdbcTemplate.query(sql, params, this::mapRow);
            return list.stream().findFirst();
        } catch (DataAccessException dae) {
            log.warn("[PlantillasContratos] Excepción al consultar codigo {} ver {}: {}", codigo, version, dae.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Long create(PlantillaContratoRequestDTO dto, Long orgId, Long userId) {
        ensureTableExists();

        String sql = """
            INSERT INTO PLANTILLAS_CONTRATOS (
                id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                contenido_html, variables_disponibles, campos_requeridos, version,
                estado, vigencia_desde, vigencia_hasta, creado_por
            ) VALUES (
                :orgId, :codigo, :nombre, :tipoContrato, :descripcion,
                :contenidoHtml, :variables, :camposRequeridos, :version,
                :estado, :vigenciaDesde, :vigenciaHasta, :userId
            )
        """;

        String varsJson = null;
        try {
            if (dto.getVariablesDisponibles() != null) {
                varsJson = objectMapper.writeValueAsString(dto.getVariablesDisponibles());
            }
        } catch (Exception ignored) {}

        String reqJson = null;
        try {
            if (dto.getCamposRequeridos() != null) {
                reqJson = objectMapper.writeValueAsString(dto.getCamposRequeridos());
            }
        } catch (Exception ignored) {}

        int ver = (dto.getVersion() != null && dto.getVersion() > 0) ? dto.getVersion() : 1;
        String est = (dto.getEstado() != null && !dto.getEstado().isBlank()) ? dto.getEstado().toUpperCase() : "ACTIVA";
        LocalDate desde = (dto.getVigenciaDesde() != null) ? dto.getVigenciaDesde() : LocalDate.now();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("codigo", dto.getCodigo().toUpperCase())
                .addValue("nombre", dto.getNombre())
                .addValue("tipoContrato", dto.getTipoContrato().toUpperCase())
                .addValue("descripcion", dto.getDescripcion())
                .addValue("contenidoHtml", dto.getContenidoHtml())
                .addValue("variables", varsJson)
                .addValue("camposRequeridos", reqJson)
                .addValue("version", ver)
                .addValue("estado", est)
                .addValue("vigenciaDesde", java.sql.Date.valueOf(desde))
                .addValue("vigenciaHasta", dto.getVigenciaHasta() != null ? java.sql.Date.valueOf(dto.getVigenciaHasta()) : null)
                .addValue("userId", userId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PLANTILLA"});
        Long generatedId = extractGeneratedKey(keyHolder, "ID_PLANTILLA");

        if (generatedId == null) {
            generatedId = jdbcTemplate.queryForObject(
                "SELECT ID_PLANTILLA FROM (SELECT ID_PLANTILLA FROM PLANTILLAS_CONTRATOS WHERE ID_ORGANIZACION = :orgId AND CODIGO = :codigo ORDER BY ID_PLANTILLA DESC) WHERE ROWNUM = 1",
                new MapSqlParameterSource("orgId", orgId).addValue("codigo", dto.getCodigo().toUpperCase()),
                Long.class
            );
        }
        return generatedId;
    }

    @Override
    public void update(Long id, PlantillaContratoRequestDTO dto) {
        String sql = """
            UPDATE PLANTILLAS_CONTRATOS SET
                nombre = :nombre,
                tipo_contrato = :tipoContrato,
                descripcion = :descripcion,
                contenido_html = :contenidoHtml,
                variables_disponibles = :variables,
                campos_requeridos = :camposRequeridos,
                vigencia_desde = :vigenciaDesde,
                vigencia_hasta = :vigenciaHasta,
                fecha_actualizacion = CURRENT_TIMESTAMP
            WHERE id_plantilla = :id
        """;

        String varsJson = null;
        try {
            if (dto.getVariablesDisponibles() != null) {
                varsJson = objectMapper.writeValueAsString(dto.getVariablesDisponibles());
            }
        } catch (Exception ignored) {}

        String reqJson = null;
        try {
            if (dto.getCamposRequeridos() != null) {
                reqJson = objectMapper.writeValueAsString(dto.getCamposRequeridos());
            }
        } catch (Exception ignored) {}

        LocalDate desde = (dto.getVigenciaDesde() != null) ? dto.getVigenciaDesde() : LocalDate.now();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", dto.getNombre())
                .addValue("tipoContrato", dto.getTipoContrato().toUpperCase())
                .addValue("descripcion", dto.getDescripcion())
                .addValue("contenidoHtml", dto.getContenidoHtml())
                .addValue("variables", varsJson)
                .addValue("camposRequeridos", reqJson)
                .addValue("vigenciaDesde", java.sql.Date.valueOf(desde))
                .addValue("vigenciaHasta", dto.getVigenciaHasta() != null ? java.sql.Date.valueOf(dto.getVigenciaHasta()) : null);

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateStatus(Long id, String estado) {
        String sql = "UPDATE PLANTILLAS_CONTRATOS SET estado = :estado, fecha_actualizacion = CURRENT_TIMESTAMP WHERE id_plantilla = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("estado", estado.toUpperCase()));
    }

    @Override
    public Integer getMaxVersion(Long orgId, String codigo) {
        try {
            String sql = "SELECT COALESCE(MAX(version), 0) FROM PLANTILLAS_CONTRATOS WHERE id_organizacion = :orgId AND codigo = :codigo";
            Integer max = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("orgId", orgId).addValue("codigo", codigo), Integer.class);
            return max != null ? max : 0;
        } catch (DataAccessException e) {
            return 0;
        }
    }
}
