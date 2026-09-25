package com.saed.backend.person.repository.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.repository.PersonaRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PersonaRepositoryImpl implements PersonaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PersonaRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PersonaDTO> rowMapper = (rs, rowNum) -> {
        Long idApto = null;
        String numApto = null;
        String tipoRelacion = null;
        try {
            long val = rs.getLong("ID_APARTAMENTO");
            if (!rs.wasNull()) idApto = val;
        } catch (Exception ignored) {}
        try {
            numApto = rs.getString("NUMERO_APARTAMENTO");
        } catch (Exception ignored) {}
        try {
            tipoRelacion = rs.getString("TIPO_RELACION");
        } catch (Exception ignored) {}

        return new PersonaDTO(
                rs.getLong("ID_PERSONA"),
                rs.getLong("ID_TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("TIPO_PERSONA"),
                rs.getString("PRIMER_NOMBRE"),
                rs.getString("SEGUNDO_NOMBRE"),
                rs.getString("PRIMER_APELLIDO"),
                rs.getString("SEGUNDO_APELLIDO"),
                rs.getString("EMAIL"),
                rs.getString("TELEFONO"),
                rs.getString("ESTADO"),
                idApto,
                numApto,
                tipoRelacion
        );
    };

    @Override
    public List<PersonaDTO> findAll(int limit, int offset) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);

        String scopeSubquery;
        String scopeWhere;

        if (propId != null) {
            params.addValue("propId", propId);
            scopeSubquery = " AND u.ID_PROPIEDAD = :propId ";
            scopeWhere = """
                AND (
                    (p.ID_PROPIEDAD = :propId AND NOT EXISTS (
                        SELECT 1 FROM USUARIOS usr 
                        JOIN USUARIO_ASIGNACIONES ua ON usr.ID_USUARIO = ua.ID_USUARIO 
                        JOIN ROLES r ON ua.ID_ROL = r.ID_ROL
                        WHERE usr.ID_PERSONA = p.ID_PERSONA 
                          AND r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION', 'ADMIN_PROPIEDAD')
                          AND NOT EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA)
                          AND NOT EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA)
                    ))
                    OR EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND u.ID_PROPIEDAD = :propId AND ru.ESTADO = 'ACTIVO')
                    OR EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND u.ID_PROPIEDAD = :propId AND pu.ESTADO = 'ACTIVO')
                )
            """;
        } else if (orgId != null && !"SUPERADMIN".equalsIgnoreCase(role)) {
            params.addValue("orgId", orgId);
            scopeSubquery = " AND EXISTS (SELECT 1 FROM PROPIEDADES pr WHERE pr.ID_PROPIEDAD = u.ID_PROPIEDAD AND pr.ID_ORGANIZACION = :orgId) ";
            scopeWhere = """
                AND (
                    (p.ID_ORGANIZACION = :orgId AND NOT EXISTS (
                        SELECT 1 FROM USUARIOS usr 
                        JOIN USUARIO_ASIGNACIONES ua ON usr.ID_USUARIO = ua.ID_USUARIO 
                        JOIN ROLES r ON ua.ID_ROL = r.ID_ROL
                        WHERE usr.ID_PERSONA = p.ID_PERSONA 
                          AND r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION')
                          AND NOT EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA)
                          AND NOT EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA)
                    ))
                    OR EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND pr.ID_ORGANIZACION = :orgId AND ru.ESTADO = 'ACTIVO')
                    OR EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pr.ID_ORGANIZACION = :orgId AND pu.ESTADO = 'ACTIVO')
                )
            """;
        } else {
            scopeSubquery = "";
            scopeWhere = "";
        }

        String sql = String.format("""
            SELECT p.*,
                   COALESCE(
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' %1$s AND ROWNUM = 1),
                       (SELECT pu.ID_UNIDAD FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' %1$s AND ROWNUM = 1),
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA %1$s AND ROWNUM = 1)
                   ) AS ID_APARTAMENTO,
                   COALESCE(
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' %1$s AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN PROPIETARIOS_UNIDAD pu ON u.ID_UNIDAD = pu.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' %1$s AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA %1$s AND ROWNUM = 1)
                   ) AS NUMERO_APARTAMENTO,
                   CASE
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' %1$s)
                            AND EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' %1$s)
                         THEN 'PROPIETARIO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' %1$s)
                         THEN 'PROPIETARIO_NO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE = 'ARRENDATARIO' %1$s)
                         THEN 'ARRENDATARIO'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE IN ('FAMILIAR', 'CONVIVIENTE', 'OTRO') %1$s)
                         THEN 'CONVIVIENTE'
                       ELSE COALESCE((SELECT ru.TIPO_RESIDENTE FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA %1$s AND ROWNUM = 1), 'RESIDENTE')
                   END AS TIPO_RELACION
            FROM PERSONAS p
            WHERE NVL(p.ESTADO, 'ACTIVO') = 'ACTIVO'
            %2$s
            ORDER BY p.ID_PERSONA DESC
            OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
            """, scopeSubquery, scopeWhere);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public Optional<PersonaDTO> findById(Long id) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        String role = ctx != null ? ctx.getRoleCode() : null;

        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        String scopeSubquery;
        String scopeWhere;

        if (propId != null) {
            params.addValue("propId", propId);
            scopeSubquery = " AND u.ID_PROPIEDAD = :propId ";
            scopeWhere = """
                AND (
                    (p.ID_PROPIEDAD = :propId AND NOT EXISTS (
                        SELECT 1 FROM USUARIOS usr 
                        JOIN USUARIO_ASIGNACIONES ua ON usr.ID_USUARIO = ua.ID_USUARIO 
                        JOIN ROLES r ON ua.ID_ROL = r.ID_ROL
                        WHERE usr.ID_PERSONA = p.ID_PERSONA 
                          AND r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION', 'ADMIN_PROPIEDAD')
                          AND NOT EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA)
                          AND NOT EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA)
                    ))
                    OR EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND u.ID_PROPIEDAD = :propId)
                    OR EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND u.ID_PROPIEDAD = :propId)
                )
            """;
        } else if (orgId != null && !"SUPERADMIN".equalsIgnoreCase(role)) {
            params.addValue("orgId", orgId);
            scopeSubquery = " AND EXISTS (SELECT 1 FROM PROPIEDADES pr WHERE pr.ID_PROPIEDAD = u.ID_PROPIEDAD AND pr.ID_ORGANIZACION = :orgId) ";
            scopeWhere = """
                AND (
                    (p.ID_ORGANIZACION = :orgId AND NOT EXISTS (
                        SELECT 1 FROM USUARIOS usr 
                        JOIN USUARIO_ASIGNACIONES ua ON usr.ID_USUARIO = ua.ID_USUARIO 
                        JOIN ROLES r ON ua.ID_ROL = r.ID_ROL
                        WHERE usr.ID_PERSONA = p.ID_PERSONA 
                          AND r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION')
                          AND NOT EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA)
                          AND NOT EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA)
                    ))
                    OR EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND pr.ID_ORGANIZACION = :orgId)
                    OR EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pr.ID_ORGANIZACION = :orgId)
                )
            """;
        } else {
            scopeSubquery = "";
            scopeWhere = "";
        }

        String sql = String.format("""
            SELECT p.*,
                   COALESCE(
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' %1$s AND ROWNUM = 1),
                       (SELECT pu.ID_UNIDAD FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' %1$s AND ROWNUM = 1),
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA %1$s AND ROWNUM = 1)
                   ) AS ID_APARTAMENTO,
                   COALESCE(
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' %1$s AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN PROPIETARIOS_UNIDAD pu ON u.ID_UNIDAD = pu.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' %1$s AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA %1$s AND ROWNUM = 1)
                   ) AS NUMERO_APARTAMENTO,
                   CASE
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' %1$s)
                            AND EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' %1$s)
                         THEN 'PROPIETARIO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' %1$s)
                         THEN 'PROPIETARIO_NO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE = 'ARRENDATARIO' %1$s)
                         THEN 'ARRENDATARIO'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE IN ('FAMILIAR', 'CONVIVIENTE', 'OTRO') %1$s)
                         THEN 'CONVIVIENTE'
                       ELSE COALESCE((SELECT ru.TIPO_RESIDENTE FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA %1$s AND ROWNUM = 1), 'RESIDENTE')
                   END AS TIPO_RELACION
            FROM PERSONAS p
            WHERE p.ID_PERSONA = :id
            %2$s
            """, scopeSubquery, scopeWhere);

        List<PersonaDTO> results = jdbcTemplate.query(sql, params, rowMapper);
        return results.stream().findFirst();
    }

    @Override
    public Long insert(PersonaRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        Long propId = ctx != null ? ctx.getPropertyId() : null;

        if (propId != null && orgId == null) {
            try {
                orgId = jdbcTemplate.queryForObject(
                        "SELECT ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = :propId",
                        new MapSqlParameterSource("propId", propId),
                        Long.class
                );
            } catch (Exception ignored) {}
        }

        String sql = """
            INSERT INTO PERSONAS (
                ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA,
                PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO,
                EMAIL, TELEFONO, ID_ORGANIZACION, ID_PROPIEDAD
            ) VALUES (
                :tipoDocumentoId, :numeroDocumento, :tipoPersona,
                :primerNombre, :segundoNombre, :primerApellido, :segundoApellido,
                :email, :telefono, :orgId, :propId
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tipoDocumentoId", request.tipoDocumentoId())
                .addValue("numeroDocumento", request.numeroDocumento())
                .addValue("tipoPersona", request.tipoPersona())
                .addValue("primerNombre", request.primerNombre())
                .addValue("segundoNombre", request.segundoNombre())
                .addValue("primerApellido", request.primerApellido())
                .addValue("segundoApellido", request.segundoApellido())
                .addValue("email", request.email())
                .addValue("telefono", request.telefono())
                .addValue("orgId", orgId)
                .addValue("propId", propId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PERSONA"});
        
        return keyHolder.getKey().longValue();
    }

    @Override
    public void update(Long id, PersonaRequestDTO request) {
        String sql = """
            UPDATE PERSONAS SET
                ID_TIPO_DOCUMENTO = :tipoDocumentoId,
                NUMERO_DOCUMENTO = :numeroDocumento,
                TIPO_PERSONA = :tipoPersona,
                PRIMER_NOMBRE = :primerNombre,
                SEGUNDO_NOMBRE = :segundoNombre,
                PRIMER_APELLIDO = :primerApellido,
                SEGUNDO_APELLIDO = :segundoApellido,
                EMAIL = :email,
                TELEFONO = :telefono
            WHERE ID_PERSONA = :id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tipoDocumentoId", request.tipoDocumentoId())
                .addValue("numeroDocumento", request.numeroDocumento())
                .addValue("tipoPersona", request.tipoPersona())
                .addValue("primerNombre", request.primerNombre())
                .addValue("segundoNombre", request.segundoNombre())
                .addValue("primerApellido", request.primerApellido())
                .addValue("segundoApellido", request.segundoApellido())
                .addValue("email", request.email())
                .addValue("telefono", request.telefono());

        jdbcTemplate.update(sql, params);
    }

    @Override
    public Optional<PersonaDTO> findByNumeroDocumento(String numeroDocumento) {
        String sql = """
            SELECT p.*,
                   COALESCE(
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT pu.ID_UNIDAD FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1)
                   ) AS ID_APARTAMENTO,
                   COALESCE(
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN PROPIETARIOS_UNIDAD pu ON u.ID_UNIDAD = pu.ID_UNIDAD WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO' AND ROWNUM = 1),
                       (SELECT u.IDENTIFICADOR FROM UNIDADES u JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1)
                   ) AS NUMERO_APARTAMENTO,
                   CASE
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO')
                            AND EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO')
                         THEN 'PROPIETARIO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA AND pu.ESTADO = 'ACTIVO')
                         THEN 'PROPIETARIO_NO_RESIDENTE'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE = 'ARRENDATARIO')
                         THEN 'ARRENDATARIO'
                       WHEN EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ru.ESTADO = 'ACTIVO' AND ru.TIPO_RESIDENTE IN ('FAMILIAR', 'CONVIVIENTE', 'OTRO'))
                          THEN 'CONVIVIENTE'
                       ELSE COALESCE((SELECT ru.TIPO_RESIDENTE FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1), 'RESIDENTE')
                   END AS TIPO_RELACION
            FROM PERSONAS p
            WHERE p.NUMERO_DOCUMENTO = :doc
              AND ROWNUM = 1
            """;
        List<PersonaDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("doc", numeroDocumento), rowMapper);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Long> findTipoDocumentoIdByCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.of(1L);
        String sql = "SELECT ID_TIPO_DOCUMENTO FROM TIPOS_DOCUMENTO WHERE UPPER(CODIGO) = UPPER(:codigo) AND ROWNUM = 1";
        try {
            List<Long> ids = jdbcTemplate.query(sql, new MapSqlParameterSource("codigo", codigo.trim()), (rs, r) -> rs.getLong("ID_TIPO_DOCUMENTO"));
            return ids.stream().findFirst();
        } catch (Exception e) {
            return Optional.of(1L);
        }
    }

    @Override
    public Optional<Long> findUnidadIdByNumero(Long propiedadId, String identificador) {
        if (identificador == null || identificador.isBlank()) return Optional.empty();
        String clean = identificador.trim();
        String sql = """
            SELECT ID_UNIDAD 
            FROM UNIDADES 
            WHERE ID_PROPIEDAD = :propId 
              AND (
                  UPPER(IDENTIFICADOR) = UPPER(:ident)
                  OR UPPER(REPLACE(IDENTIFICADOR, 'APTO ', '')) = UPPER(:ident)
                  OR UPPER(IDENTIFICADOR) LIKE '%' || UPPER(:ident)
              )
              AND ROWNUM = 1
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", propiedadId)
                .addValue("ident", clean);
        try {
            List<Long> ids = jdbcTemplate.query(sql, params, (rs, r) -> rs.getLong("ID_UNIDAD"));
            return ids.stream().findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void asignarUnidad(Long personaId, Long unidadId, String tipoRelacion) {
        String tr = tipoRelacion != null ? tipoRelacion.trim().toUpperCase() : "ARRENDATARIO";
        boolean esPropietarioDominio = "PROPIETARIO_RESIDENTE".equals(tr)
                || "PROPIETARIO_NO_RESIDENTE".equals(tr)
                || "PROPIETARIO".equals(tr);
        boolean esHabitanteFisico = "PROPIETARIO_RESIDENTE".equals(tr)
                || "ARRENDATARIO".equals(tr)
                || "CONVIVIENTE".equals(tr)
                || "FAMILIAR".equals(tr);

        // 1. PROPIETARIOS_UNIDAD
        if (esPropietarioDominio) {
            Integer countProp = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    new MapSqlParameterSource().addValue("unitId", unidadId).addValue("personaId", personaId),
                    Integer.class
            );
            if (countProp == null || countProp == 0) {
                jdbcTemplate.update(
                        "INSERT INTO PROPIETARIOS_UNIDAD (ID_UNIDAD, ID_PERSONA, PORCENTAJE_PROPIEDAD, ES_PRINCIPAL, ESTADO, FECHA_INICIO) " +
                        "VALUES (:unitId, :personaId, 100, 'S', 'ACTIVO', TRUNC(SYSDATE))",
                        new MapSqlParameterSource().addValue("unitId", unidadId).addValue("personaId", personaId)
                );
            } else {
                jdbcTemplate.update(
                        "UPDATE PROPIETARIOS_UNIDAD SET ESTADO = 'ACTIVO', ES_PRINCIPAL = 'S' WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                        new MapSqlParameterSource().addValue("unitId", unidadId).addValue("personaId", personaId)
                );
            }
        } else {
            jdbcTemplate.update(
                    "UPDATE PROPIETARIOS_UNIDAD SET ESTADO = 'INACTIVO' WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    new MapSqlParameterSource().addValue("unitId", unidadId).addValue("personaId", personaId)
            );
        }

        // 2. RESIDENTES_UNIDAD
        if (esHabitanteFisico) {
            String tipoResidenteDb;
            if ("PROPIETARIO_RESIDENTE".equals(tr) || "PROPIETARIO".equals(tr)) {
                tipoResidenteDb = "PROPIETARIO";
            } else if ("ARRENDATARIO".equals(tr)) {
                tipoResidenteDb = "ARRENDATARIO";
            } else {
                tipoResidenteDb = "FAMILIAR";
            }

            Integer countRes = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    new MapSqlParameterSource().addValue("unitId", unidadId).addValue("personaId", personaId),
                    Integer.class
            );

            if (countRes == null || countRes == 0) {
                jdbcTemplate.update(
                        "INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO, FECHA_INICIO) " +
                        "VALUES (:unitId, :personaId, :tipoResidente, 'ACTIVO', TRUNC(SYSDATE))",
                        new MapSqlParameterSource()
                                .addValue("unitId", unidadId)
                                .addValue("personaId", personaId)
                                .addValue("tipoResidente", tipoResidenteDb)
                );
            } else {
                jdbcTemplate.update(
                        "UPDATE RESIDENTES_UNIDAD SET TIPO_RESIDENTE = :tipoResidente, ESTADO = 'ACTIVO' WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                        new MapSqlParameterSource()
                                .addValue("unitId", unidadId)
                                .addValue("personaId", personaId)
                                .addValue("tipoResidente", tipoResidenteDb)
                );
            }
        } else {
            jdbcTemplate.update(
                    "UPDATE RESIDENTES_UNIDAD SET ESTADO = 'INACTIVO' WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    new MapSqlParameterSource().addValue("unitId", unidadId).addValue("personaId", personaId)
            );
        }

        // 3. Retroalimentar ID_PROPIEDAD e ID_ORGANIZACION en PERSONAS si están nulos
        try {
            jdbcTemplate.update(
                "UPDATE PERSONAS SET " +
                "ID_PROPIEDAD = (SELECT ID_PROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = :unitId), " +
                "ID_ORGANIZACION = (SELECT pr.ID_ORGANIZACION FROM UNIDADES u JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD WHERE u.ID_UNIDAD = :unitId) " +
                "WHERE ID_PERSONA = :personaId AND ID_PROPIEDAD IS NULL",
                new MapSqlParameterSource().addValue("unitId", unidadId).addValue("personaId", personaId)
            );
        } catch (Exception ignored) {}
    }

    @Override
    public void delete(Long id) {
        // Requisitos #14 y #15: Desvinculación protegida, soft-delete y preservación de trazabilidad histórica
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        jdbcTemplate.update("UPDATE PERSONAS SET ESTADO = 'INACTIVO' WHERE ID_PERSONA = :id", params);
        jdbcTemplate.update("UPDATE RESIDENTES_UNIDAD SET ESTADO = 'INACTIVO' WHERE ID_PERSONA = :id AND ESTADO = 'ACTIVO'", params);
        jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'INACTIVO' WHERE ID_PERSONA = :id AND ESTADO = 'ACTIVO'", params);
    }
}
