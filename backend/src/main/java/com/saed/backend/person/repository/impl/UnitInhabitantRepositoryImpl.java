package com.saed.backend.person.repository.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UnitInhabitantRepositoryImpl implements UnitInhabitantRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UnitInhabitantRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PersonaDTO> personaRowMapper = (rs, rowNum) -> new PersonaDTO(
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
            rs.getString("ESTADO_PERSONA")
    );

    private final RowMapper<UnitOwnerDTO> ownerRowMapper = (rs, rowNum) -> {
        PersonaDTO persona = personaRowMapper.mapRow(rs, rowNum);
        return new UnitOwnerDTO(
                rs.getLong("ID_PROPIETARIO_UNIDAD"),
                persona,
                rs.getBigDecimal("PORCENTAJE_PROPIEDAD"),
                rs.getString("ES_PRINCIPAL"),
                rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
                rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
                rs.getString("ESTADO_PROPIETARIO")
        );
    };

    private final RowMapper<UnitResidentDTO> residentRowMapper = (rs, rowNum) -> {
        PersonaDTO persona = personaRowMapper.mapRow(rs, rowNum);
        return new UnitResidentDTO(
                rs.getLong("ID_RESIDENTE_UNIDAD"),
                persona,
                rs.getString("TIPO_RESIDENTE"),
                rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
                rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
                rs.getString("ESTADO_RESIDENTE")
        );
    };

    @Override
    public List<UnitOwnerDTO> findOwnersByUnitId(Long unitId) {
        String sql = """
            SELECT pu.ID_PROPIETARIO_UNIDAD, pu.PORCENTAJE_PROPIEDAD, pu.ES_PRINCIPAL, pu.FECHA_INICIO, pu.FECHA_FIN, pu.ESTADO as ESTADO_PROPIETARIO,
                   p.ID_PERSONA, p.ID_TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.TIPO_PERSONA, p.PRIMER_NOMBRE, p.SEGUNDO_NOMBRE, 
                   p.PRIMER_APELLIDO, p.SEGUNDO_APELLIDO, p.EMAIL, p.TELEFONO, p.ESTADO as ESTADO_PERSONA
            FROM PROPIETARIOS_UNIDAD pu
            JOIN PERSONAS p ON pu.ID_PERSONA = p.ID_PERSONA
            WHERE pu.ID_UNIDAD = :unitId
            ORDER BY pu.ES_PRINCIPAL DESC, pu.PORCENTAJE_PROPIEDAD DESC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("unitId", unitId), ownerRowMapper);
    }

    @Override
    public Long insertOwner(Long unitId, UnitOwnerRequestDTO request) {
        String sql = """
            INSERT INTO PROPIETARIOS_UNIDAD (
                ID_UNIDAD, ID_PERSONA, PORCENTAJE_PROPIEDAD, ES_PRINCIPAL
            ) VALUES (
                :unitId, :personaId, :porcentajePropiedad, :esPrincipal
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unitId", unitId)
                .addValue("personaId", request.personaId())
                .addValue("porcentajePropiedad", request.porcentajePropiedad())
                .addValue("esPrincipal", request.esPrincipal());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PROPIETARIO_UNIDAD"});
        
        return keyHolder.getKey().longValue();
    }

    @Override
    public List<UnitResidentDTO> findResidentsByUnitId(Long unitId) {
        String sql = """
            SELECT DISTINCT COALESCE(ru.ID_RESIDENTE_UNIDAD, ua.ID_ASIGNACION) as ID_RESIDENTE_UNIDAD,
                   COALESCE(ru.TIPO_RESIDENTE, r.CODIGO, 'RESIDENTE') as TIPO_RESIDENTE,
                   COALESCE(ru.FECHA_INICIO, ua.FECHA_INICIO, TRUNC(SYSDATE)) as FECHA_INICIO,
                   ru.FECHA_FIN,
                   COALESCE(ru.ESTADO, ua.ESTADO, 'ACTIVO') as ESTADO_RESIDENTE,
                   p.ID_PERSONA, p.ID_TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.TIPO_PERSONA, p.PRIMER_NOMBRE, p.SEGUNDO_NOMBRE, 
                   p.PRIMER_APELLIDO, p.SEGUNDO_APELLIDO, p.EMAIL, p.TELEFONO, p.ESTADO as ESTADO_PERSONA
            FROM PERSONAS p
            LEFT JOIN RESIDENTES_UNIDAD ru ON p.ID_PERSONA = ru.ID_PERSONA AND ru.ID_UNIDAD = :unitId AND ru.ESTADO IN ('ACTIVO', 'ACTIVA')
            LEFT JOIN USUARIOS u ON p.ID_PERSONA = u.ID_PERSONA
            LEFT JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO AND ua.ID_UNIDAD = :unitId AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
            LEFT JOIN ROLES r ON ua.ID_ROL = r.ID_ROL
            WHERE (ru.ID_UNIDAD = :unitId OR ua.ID_UNIDAD = :unitId)
            ORDER BY FECHA_INICIO DESC
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("unitId", unitId), residentRowMapper);
    }

    @Override
    public Long insertResident(Long unitId, UnitResidentRequestDTO request) {
        String sql = """
            INSERT INTO RESIDENTES_UNIDAD (
                ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE
            ) VALUES (
                :unitId, :personaId, :tipoResidente
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unitId", unitId)
                .addValue("personaId", request.personaId())
                .addValue("tipoResidente", request.tipoResidente());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_RESIDENTE_UNIDAD"});
        
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<UnitResidentDTO> findResidentByIdAndUnitId(Long unitId, Long residentId) {
        String sql = """
            SELECT ru.ID_RESIDENTE_UNIDAD, ru.TIPO_RESIDENTE, ru.FECHA_INICIO, ru.FECHA_FIN, ru.ESTADO as ESTADO_RESIDENTE,
                   p.ID_PERSONA, p.ID_TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.TIPO_PERSONA, p.PRIMER_NOMBRE, p.SEGUNDO_NOMBRE,
                   p.PRIMER_APELLIDO, p.SEGUNDO_APELLIDO, p.EMAIL, p.TELEFONO, p.ESTADO as ESTADO_PERSONA
            FROM RESIDENTES_UNIDAD ru
            JOIN PERSONAS p ON ru.ID_PERSONA = p.ID_PERSONA
            WHERE ru.ID_UNIDAD = :unitId AND (ru.ID_RESIDENTE_UNIDAD = :residentId OR ru.ID_PERSONA = :residentId)
            """;
        List<UnitResidentDTO> list = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("unitId", unitId).addValue("residentId", residentId),
                residentRowMapper
        );
        return list.stream().findFirst();
    }

    @Override
    public int updateResidentStatus(Long unitId, Long residentId, String status) {
        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unitId", unitId)
                .addValue("residentId", residentId)
                .addValue("status", status.toUpperCase());

        if ("INACTIVO".equalsIgnoreCase(status)) {
            sql = """
                UPDATE RESIDENTES_UNIDAD
                SET ESTADO = :status, FECHA_FIN = NULL
                WHERE ID_UNIDAD = :unitId AND (ID_RESIDENTE_UNIDAD = :residentId OR ID_PERSONA = :residentId)
                """;
        } else {
            sql = """
                UPDATE RESIDENTES_UNIDAD
                SET ESTADO = :status, FECHA_FIN = NULL
                WHERE ID_UNIDAD = :unitId AND (ID_RESIDENTE_UNIDAD = :residentId OR ID_PERSONA = :residentId)
                """;
        }
        return jdbcTemplate.update(sql, params);
    }

    @Override
    public int unlinkResident(Long unitId, Long residentId) {
        Long personaId = null;
        try {
            List<Long> pList = jdbcTemplate.query(
                "SELECT ID_PERSONA FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = :unitId AND (ID_RESIDENTE_UNIDAD = :residentId OR ID_PERSONA = :residentId)",
                new MapSqlParameterSource("unitId", unitId).addValue("residentId", residentId),
                (rs, rowNum) -> rs.getLong(1)
            );
            if (!pList.isEmpty()) personaId = pList.get(0);
        } catch (Exception ignored) {}

        String sql = """
            UPDATE RESIDENTES_UNIDAD
            SET ESTADO = 'INACTIVO', FECHA_FIN = TRUNC(SYSDATE)
            WHERE ID_UNIDAD = :unitId AND (ID_RESIDENTE_UNIDAD = :residentId OR ID_PERSONA = :residentId)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unitId", unitId)
                .addValue("residentId", residentId);
        int updated = jdbcTemplate.update(sql, params);

        // Desactivar asignación de usuario correspondiente si existe
        if (personaId != null) {
            SaedContext prevCtx = SaedContextHolder.getContext();
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

                String sqlAsignacion = """
                    UPDATE USUARIO_ASIGNACIONES
                    SET ESTADO = 'INACTIVA', FECHA_FIN = TRUNC(SYSDATE)
                    WHERE ID_UNIDAD = :unitId
                      AND ID_USUARIO IN (
                          SELECT u.ID_USUARIO
                          FROM USUARIOS u
                          WHERE u.ID_PERSONA = :personaId
                      )
                    """;
                jdbcTemplate.update(sqlAsignacion, new MapSqlParameterSource("unitId", unitId).addValue("personaId", personaId));

                String sqlUsuario = """
                    UPDATE USUARIOS
                    SET ESTADO = 'INACTIVO'
                    WHERE ID_PERSONA = :personaId
                    """;
                jdbcTemplate.update(sqlUsuario, Map.of("personaId", personaId));
            } catch (Exception ignored) {
            } finally {
                if (prevCtx != null) {
                    SaedContextHolder.setContext(prevCtx);
                    try {
                        jdbcTemplate.getJdbcOperations().execute(
                            String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, '%s'); END;",
                                prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : 1L,
                                prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : 1L,
                                prevCtx.getRoleCode() != null ? prevCtx.getRoleCode() : "RESIDENTE"
                            )
                        );
                    } catch (Exception ignored) {}
                }
            }
        }

        return updated;
    }

    @Override
    public int deleteResidentPermanently(Long unitId, Long residentId) {
        Long personaId = null;
        try {
            List<Long> pList = jdbcTemplate.query(
                "SELECT ID_PERSONA FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = :unitId AND (ID_RESIDENTE_UNIDAD = :residentId OR ID_PERSONA = :residentId)",
                new MapSqlParameterSource("unitId", unitId).addValue("residentId", residentId),
                (rs, rowNum) -> rs.getLong(1)
            );
            if (!pList.isEmpty()) personaId = pList.get(0);
        } catch (Exception ignored) {}

        String sql = """
            DELETE FROM RESIDENTES_UNIDAD
            WHERE ID_UNIDAD = :unitId AND (ID_RESIDENTE_UNIDAD = :residentId OR ID_PERSONA = :residentId)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unitId", unitId)
                .addValue("residentId", residentId);
        int deleted = jdbcTemplate.update(sql, params);

        if (personaId != null) {
            SaedContext prevCtx = SaedContextHolder.getContext();
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

                // Eliminar o inactivar asignaciones de usuario asociadas a esta unidad
                String sqlAsignacion = """
                    DELETE FROM USUARIO_ASIGNACIONES
                    WHERE ID_UNIDAD = :unitId
                      AND ID_USUARIO IN (
                          SELECT u.ID_USUARIO
                          FROM USUARIOS u
                          WHERE u.ID_PERSONA = :personaId
                      )
                    """;
                jdbcTemplate.update(sqlAsignacion, new MapSqlParameterSource("unitId", unitId).addValue("personaId", personaId));

                // Si el usuario no tiene más asignaciones, marcar como inactivo
                List<Long> uList = jdbcTemplate.query(
                    "SELECT ID_USUARIO FROM USUARIOS WHERE ID_PERSONA = :personaId",
                    Map.of("personaId", personaId),
                    (rs, rowNum) -> rs.getLong(1)
                );
                for (Long uid : uList) {
                    Integer countAsig = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :uid AND ESTADO = 'ACTIVA'",
                        Map.of("uid", uid),
                        Integer.class
                    );
                    if (countAsig == null || countAsig == 0) {
                        jdbcTemplate.update("UPDATE USUARIOS SET ESTADO = 'INACTIVO' WHERE ID_USUARIO = :uid", Map.of("uid", uid));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (prevCtx != null) {
                    SaedContextHolder.setContext(prevCtx);
                    try {
                        jdbcTemplate.getJdbcOperations().execute(
                            String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, '%s'); END;",
                                prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : 1L,
                                prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : 1L,
                                prevCtx.getRoleCode() != null ? prevCtx.getRoleCode() : "RESIDENTE"
                            )
                        );
                    } catch (Exception ignored) {}
                }
            }
        }

        return deleted;
    }
}
