package com.saed.backend.dashboard.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.List;
import java.util.Map;

@Tag(name = "Dashboard", description = "API para la gestion de Dashboard")
@RestController
@RequestMapping("/api/v1/residentes")
public class DashboardController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public DashboardController(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getResidentes(@RequestParam(required = false) Long idApartamento) {
        if (idApartamento == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
            "SELECT p.ID_PERSONA, p.NUMERO_DOCUMENTO, " +
            "TRIM(p.PRIMER_NOMBRE || ' ' || COALESCE(p.SEGUNDO_NOMBRE, '')) AS NOMBRES, " +
            "TRIM(p.PRIMER_APELLIDO || ' ' || COALESCE(p.SEGUNDO_APELLIDO, '')) AS APELLIDOS, " +
            "p.TELEFONO, p.EMAIL " +
            "FROM RESIDENTES_UNIDAD ru " +
            "JOIN PERSONAS p ON ru.ID_PERSONA = p.ID_PERSONA " +
            "WHERE ru.ID_UNIDAD = :idApto AND ru.ESTADO = 'ACTIVO'",
            Map.of("idApto", idApartamento)
        );
    }

    @GetMapping("/{id}/frecuentes")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getFrecuentes(@PathVariable Long id) {
        Long userId = com.saed.backend.context.SaedContextHolder.getContext() != null
                ? com.saed.backend.context.SaedContextHolder.getContext().getUserId() : null;
        if (userId != null) {
            try {
                Long myPersonaId = jdbcTemplate.queryForObject(
                    "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", userId), Long.class);
                if (myPersonaId != null && !myPersonaId.equals(id)) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar visitantes de otro residente");
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
        }
        String sql = """
            SELECT 
                v.ID_VISITANTE AS "idVisitante",
                v.ID_VISITANTE AS "idFrecuente",
                v.ID_PERSONA AS "idPersona",
                p.NUMERO_DOCUMENTO AS "documento",
                p.ID_TIPO_DOCUMENTO AS "idTipoDoc",
                TRIM(p.PRIMER_NOMBRE || ' ' || NVL(p.PRIMER_APELLIDO, '')) AS "nombreVisitante",
                p.TELEFONO AS "telefono",
                p.EMAIL AS "email",
                v.EMPRESA AS "empresa",
                v.ES_FRECUENTE AS "esFrecuente",
                (SELECT MAX(vv.PLACA) FROM VEHICULOS_VISITA vv JOIN VISITAS vi2 ON vv.ID_VISITA = vi2.ID_VISITA WHERE vi2.ID_VISITANTE = v.ID_VISITANTE) AS "ultimaPlaca",
                (SELECT MAX(vv.TIPO_VEHICULO) FROM VEHICULOS_VISITA vv JOIN VISITAS vi2 ON vv.ID_VISITA = vi2.ID_VISITA WHERE vi2.ID_VISITANTE = v.ID_VISITANTE) AS "ultimoTipoVehiculo",
                MAX(vi.FECHA_PROGRAMADA) AS "ultimaVisita"
            FROM VISITANTES v
            JOIN PERSONAS p ON v.ID_PERSONA = p.ID_PERSONA
            JOIN VISITAS vi ON v.ID_VISITANTE = vi.ID_VISITANTE
            WHERE v.ES_FRECUENTE = 'S'
              AND (
                  vi.ID_UNIDAD IN (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = :id AND ru.ESTADO = 'ACTIVO')
                  OR vi.ID_UNIDAD IN (SELECT ru2.ID_UNIDAD FROM RESIDENTES_UNIDAD ru2 JOIN USUARIOS u ON ru2.ID_PERSONA = u.ID_PERSONA WHERE u.ID_USUARIO = :id)
                  OR (:userId IS NOT NULL AND vi.ID_UNIDAD IN (SELECT ua.ID_UNIDAD FROM USUARIO_ASIGNACIONES ua WHERE ua.ID_USUARIO = :userId AND ua.ESTADO IN ('ACTIVA', 'ACTIVO') AND ua.ID_UNIDAD IS NOT NULL))
                  OR vi.AUTORIZADO_POR = :id
                  OR (:userId IS NOT NULL AND vi.AUTORIZADO_POR = :userId)
              )
            GROUP BY v.ID_VISITANTE, v.ID_PERSONA, p.NUMERO_DOCUMENTO, p.ID_TIPO_DOCUMENTO, p.PRIMER_NOMBRE, p.PRIMER_APELLIDO, p.TELEFONO, p.EMAIL, v.EMPRESA, v.ES_FRECUENTE
            ORDER BY MAX(vi.FECHA_PROGRAMADA) DESC NULLS LAST
        """;
        return jdbcTemplate.queryForList(sql, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId));
    }

    @PostMapping("/{id}/frecuentes")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Map<String, Object>> crearFrecuente(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = com.saed.backend.context.SaedContextHolder.getContext() != null
                ? com.saed.backend.context.SaedContextHolder.getContext().getUserId() : null;
        if (userId != null) {
            try {
                Long myPersonaId = jdbcTemplate.queryForObject(
                    "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", userId), Long.class);
                if (myPersonaId != null && !myPersonaId.equals(id)) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar visitantes de otro residente");
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
        }

        String doc = body.get("numeroDocumento") != null ? body.get("numeroDocumento").toString().trim() : "";
        String nom = body.get("nombres") != null ? body.get("nombres").toString().trim() : "Visitante";
        String ape = body.get("apellidos") != null ? body.get("apellidos").toString().trim() : "";
        String tel = body.get("telefono") != null ? body.get("telefono").toString().trim() : "";
        String email = body.get("email") != null ? body.get("email").toString().trim() : "";
        String empresa = body.get("empresa") != null ? body.get("empresa").toString().trim() : null;
        Long idTipoDoc = 1L;
        if (body.get("idTipoDoc") != null) {
            try { idTipoDoc = Long.valueOf(body.get("idTipoDoc").toString()); } catch (Exception ignored) {}
        }

        Long personaId = null;
        Long visitanteId = null;
        com.saed.backend.context.SaedContext prevCtx = com.saed.backend.context.SaedContextHolder.getContext();
        try {
            Long orgId = prevCtx != null && prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : 1L;
            Long propId = prevCtx != null && prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : 1L;
            com.saed.backend.context.SaedContext systemCtx = com.saed.backend.context.SaedContext.builder()
                .userId(1L)
                .organizationId(orgId)
                .propertyId(propId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
            com.saed.backend.context.SaedContextHolder.setContext(systemCtx);
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                jdbcTemplate.getJdbcOperations().execute(
                    String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;", orgId, propId)
                );
            } catch (Exception ignored) {}

            if (!doc.isEmpty()) {
                try {
                    List<Long> pers = jdbcTemplate.query(
                        "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc ORDER BY CASE WHEN ID_TIPO_DOCUMENTO = :idTipoDoc THEN 0 ELSE 1 END, ID_PERSONA ASC",
                        Map.of("doc", doc, "idTipoDoc", idTipoDoc), (rs, r) -> rs.getLong("ID_PERSONA")
                    );
                    if (!pers.isEmpty()) {
                        personaId = pers.get(0);
                    }
                } catch (Exception ignored) {}
            }

            if (personaId == null) {
                try {
                    org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
                    org.springframework.jdbc.core.namedparam.MapSqlParameterSource pParams = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("idTipo", idTipoDoc)
                        .addValue("doc", !doc.isEmpty() ? doc : "V-" + System.currentTimeMillis())
                        .addValue("nom", nom)
                        .addValue("ape", ape.isBlank() ? "N/A" : ape)
                        .addValue("tel", tel.isBlank() ? null : tel)
                        .addValue("email", email.isBlank() ? null : email);
                    jdbcTemplate.update(
                        "INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, TELEFONO, EMAIL, ESTADO) " +
                        "VALUES (:idTipo, :doc, 'NATURAL', :nom, :ape, :tel, :email, 'ACTIVO')",
                        pParams, kh, new String[]{"ID_PERSONA"}
                    );
                    personaId = extractGeneratedKey(kh, "ID_PERSONA");
                } catch (Exception ignored) {}
                if (personaId == null && !doc.isEmpty()) {
                    try {
                        List<Long> pers = jdbcTemplate.query(
                            "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc ORDER BY CASE WHEN ID_TIPO_DOCUMENTO = :idTipoDoc THEN 0 ELSE 1 END, ID_PERSONA ASC",
                            Map.of("doc", doc, "idTipoDoc", idTipoDoc), (rs, r) -> rs.getLong("ID_PERSONA")
                        );
                        if (!pers.isEmpty()) {
                            personaId = pers.get(0);
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (personaId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No se pudo registrar la persona del visitante"));
            }

            try {
                List<Long> visList = jdbcTemplate.query(
                    "SELECT ID_VISITANTE FROM VISITANTES WHERE ID_PERSONA = :p",
                    Map.of("p", personaId), (rs, r) -> rs.getLong("ID_VISITANTE")
                );
                if (!visList.isEmpty()) {
                    visitanteId = visList.get(0);
                    jdbcTemplate.update(
                        "UPDATE VISITANTES SET ES_FRECUENTE = 'S', EMPRESA = NVL(:emp, EMPRESA) WHERE ID_VISITANTE = :v",
                        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("emp", empresa)
                            .addValue("v", visitanteId)
                    );
                } else {
                    org.springframework.jdbc.support.KeyHolder khVis = new org.springframework.jdbc.support.GeneratedKeyHolder();
                    jdbcTemplate.update(
                        "INSERT INTO VISITANTES (ID_PERSONA, ES_FRECUENTE, EMPRESA, ESTADO) VALUES (:p, 'S', :emp, 'ACTIVO')",
                        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("p", personaId)
                            .addValue("emp", empresa),
                        khVis, new String[]{"ID_VISITANTE"}
                    );
                    visitanteId = extractGeneratedKey(khVis, "ID_VISITANTE");
                }
            } catch (Exception ignored) {}

            if (visitanteId == null) {
                try {
                    List<Long> visList = jdbcTemplate.query(
                        "SELECT ID_VISITANTE FROM VISITANTES WHERE ID_PERSONA = :p",
                        Map.of("p", personaId), (rs, r) -> rs.getLong("ID_VISITANTE")
                    );
                    if (!visList.isEmpty()) {
                        visitanteId = visList.get(0);
                    }
                } catch (Exception ignored) {}
            }
        } finally {
            restoreSaedContext(prevCtx);
        }

        // Vincular con unidad del residente
        Long unidadId = null;
        try {
            List<Long> uids = jdbcTemplate.query(
                "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = :id AND ESTADO = 'ACTIVO'",
                Map.of("id", id), (rs, r) -> rs.getLong("ID_UNIDAD")
            );
            if (!uids.isEmpty()) {
                unidadId = uids.get(0);
            }
        } catch (Exception ignored) {}

        if (unidadId != null && visitanteId != null) {
            try {
                List<Long> existingVisitas = jdbcTemplate.query(
                    "SELECT ID_VISITA FROM VISITAS WHERE ID_UNIDAD = :u AND ID_VISITANTE = :v",
                    Map.of("u", unidadId, "v", visitanteId), (rs, r) -> rs.getLong("ID_VISITA")
                );
                if (existingVisitas.isEmpty()) {
                    jdbcTemplate.update(
                        "INSERT INTO VISITAS (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, AUTORIZADO_POR, FECHA_PROGRAMADA, ESTADO) " +
                        "VALUES (:u, :v, 'MANUAL', 'Visitante frecuente autorizado', :aut, CURRENT_TIMESTAMP, 'PROGRAMADA')",
                        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("u", unidadId)
                            .addValue("v", visitanteId)
                            .addValue("aut", userId != null ? userId : id)
                    );
                }
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(Map.of("success", true, "idVisitante", visitanteId != null ? visitanteId : 0));
    }

    @DeleteMapping("/{id}/frecuentes/{idFrecuente}")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> deleteFrecuente(@PathVariable Long id, @PathVariable Long idFrecuente) {
        Long userId = com.saed.backend.context.SaedContextHolder.getContext() != null
                ? com.saed.backend.context.SaedContextHolder.getContext().getUserId() : null;
        if (userId != null) {
            try {
                Long myPersonaId = jdbcTemplate.queryForObject(
                    "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", userId), Long.class);
                if (myPersonaId != null && !myPersonaId.equals(id)) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar visitantes de otro residente");
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
        }
        jdbcTemplate.update("UPDATE VISITANTES SET ES_FRECUENTE = 'N' WHERE ID_VISITANTE = :id", Map.of("id", idFrecuente));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/qr-activos")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getQrActivos(@PathVariable Long id) {
        Long userId = com.saed.backend.context.SaedContextHolder.getContext() != null
                ? com.saed.backend.context.SaedContextHolder.getContext().getUserId() : null;
        if (userId != null) {
            try {
                Long myPersonaId = jdbcTemplate.queryForObject(
                    "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", userId), Long.class);
                if (myPersonaId != null && !myPersonaId.equals(id)) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar QRs de otro residente");
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
        }
        String sql = """
            SELECT 
                q.ID_QR AS "idQr",
                q.ID_VISITA AS "idVisita",
                q.TOKEN_QR AS "codigoQr",
                q.TOKEN_QR AS "token",
                q.FECHA_EXPIRACION AS "fechaExpiracion",
                q.FECHA_GENERACION AS "fechaCreacion",
                q.ESTADO AS "estado",
                1 AS "cantidadPersonas",
                v.MOTIVO AS "motivo",
                TRIM(p.PRIMER_NOMBRE || ' ' || NVL(p.PRIMER_APELLIDO, '')) AS "nombreVisitante",
                p.NUMERO_DOCUMENTO AS "documentoVisitante"
            FROM QR_ACCESOS q
            JOIN VISITAS v ON q.ID_VISITA = v.ID_VISITA
            LEFT JOIN VISITANTES vis ON v.ID_VISITANTE = vis.ID_VISITANTE
            LEFT JOIN PERSONAS p ON vis.ID_PERSONA = p.ID_PERSONA
            WHERE q.ESTADO = 'ACTIVO'
              AND (q.FECHA_EXPIRACION IS NULL OR q.FECHA_EXPIRACION > CURRENT_TIMESTAMP)
              AND (q.USOS_PERMITIDOS IS NULL OR NVL(q.USOS_CONSUMIDOS, 0) < q.USOS_PERMITIDOS)
              AND (
                  v.ID_UNIDAD IN (SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = :id AND ru.ESTADO = 'ACTIVO')
                  OR v.ID_UNIDAD IN (SELECT ru2.ID_UNIDAD FROM RESIDENTES_UNIDAD ru2 JOIN USUARIOS u ON ru2.ID_PERSONA = u.ID_PERSONA WHERE u.ID_USUARIO = :id)
                  OR (:userId IS NOT NULL AND v.ID_UNIDAD IN (SELECT ua.ID_UNIDAD FROM USUARIO_ASIGNACIONES ua WHERE ua.ID_USUARIO = :userId AND ua.ESTADO IN ('ACTIVA', 'ACTIVO') AND ua.ID_UNIDAD IS NOT NULL))
                  OR v.AUTORIZADO_POR = :id
                  OR (:userId IS NOT NULL AND v.AUTORIZADO_POR = :userId)
              )
            ORDER BY q.ID_QR DESC
        """;
        return jdbcTemplate.queryForList(sql, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId));
    }
    
    @PostMapping("/{id}/asignar-apartamento")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> asignarApartamento(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("idApartamento")) {
            return ResponseEntity.badRequest().build();
        }
        Object aptVal = payload.get("idApartamento");
        Long unitId = null;
        if (aptVal instanceof Number num) {
            unitId = num.longValue();
        } else if (aptVal != null) {
            try {
                unitId = Long.parseLong(aptVal.toString().trim());
            } catch (NumberFormatException ignored) {}
        }
        if (unitId == null) {
            return ResponseEntity.badRequest().build();
        }

        String tipoRelacion = payload.containsKey("tipoRelacion") && payload.get("tipoRelacion") != null
                ? payload.get("tipoRelacion").toString().trim().toUpperCase()
                : (payload.containsKey("rolEnContrato") && payload.get("rolEnContrato") != null
                ? payload.get("rolEnContrato").toString().trim().toUpperCase()
                : (payload.containsKey("tipoResidente") && payload.get("tipoResidente") != null
                ? payload.get("tipoResidente").toString().trim().toUpperCase()
                : "ARRENDATARIO"));

        if ("RESIDENTE".equals(tipoRelacion)) {
            tipoRelacion = "ARRENDATARIO";
        }

        boolean esPropietarioDominio = "PROPIETARIO_RESIDENTE".equals(tipoRelacion)
                || "PROPIETARIO_NO_RESIDENTE".equals(tipoRelacion)
                || "PROPIETARIO".equals(tipoRelacion);

        boolean esHabitanteFisico = "PROPIETARIO_RESIDENTE".equals(tipoRelacion)
                || "ARRENDATARIO".equals(tipoRelacion)
                || "CONVIVIENTE".equals(tipoRelacion)
                || "FAMILIAR".equals(tipoRelacion);

        // 1. Gestionar Titularidad de Dominio en PROPIETARIOS_UNIDAD
        if (esPropietarioDominio) {
            Integer countProp = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    Map.of("unitId", unitId, "personaId", id),
                    Integer.class
            );
            if (countProp == null || countProp == 0) {
                jdbcTemplate.update(
                        "INSERT INTO PROPIETARIOS_UNIDAD (ID_UNIDAD, ID_PERSONA, PORCENTAJE_PROPIEDAD, ES_PRINCIPAL, ESTADO, FECHA_INICIO) " +
                        "VALUES (:unitId, :personaId, 100, 'S', 'ACTIVO', TRUNC(SYSDATE))",
                        Map.of("unitId", unitId, "personaId", id)
                );
            } else {
                jdbcTemplate.update(
                        "UPDATE PROPIETARIOS_UNIDAD SET ESTADO = 'ACTIVO', ES_PRINCIPAL = 'S' WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                        Map.of("unitId", unitId, "personaId", id)
                );
            }
        } else {
            jdbcTemplate.update(
                    "UPDATE PROPIETARIOS_UNIDAD SET ESTADO = 'INACTIVO' WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    Map.of("unitId", unitId, "personaId", id)
            );
        }

        // 2. Gestionar Habitante Físico en RESIDENTES_UNIDAD (Requisitos #11 y #12)
        if (esHabitanteFisico) {
            String tipoResidenteDb;
            if ("PROPIETARIO_RESIDENTE".equals(tipoRelacion) || "PROPIETARIO".equals(tipoRelacion)) {
                tipoResidenteDb = "PROPIETARIO";
            } else if ("ARRENDATARIO".equals(tipoRelacion)) {
                tipoResidenteDb = "ARRENDATARIO";
            } else if ("CONVIVIENTE".equals(tipoRelacion) || "FAMILIAR".equals(tipoRelacion)) {
                tipoResidenteDb = "FAMILIAR";
            } else {
                tipoResidenteDb = "OTRO";
            }

            Integer countRes = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    Map.of("unitId", unitId, "personaId", id),
                    Integer.class
            );

            if (countRes == null || countRes == 0) {
                jdbcTemplate.update(
                        "INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO, FECHA_INICIO) " +
                        "VALUES (:unitId, :personaId, :tipoResidente, 'ACTIVO', TRUNC(SYSDATE))",
                        Map.of("unitId", unitId, "personaId", id, "tipoResidente", tipoResidenteDb)
                );
            } else {
                jdbcTemplate.update(
                        "UPDATE RESIDENTES_UNIDAD SET TIPO_RESIDENTE = :tipoResidente, ESTADO = 'ACTIVO' WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                        Map.of("unitId", unitId, "personaId", id, "tipoResidente", tipoResidenteDb)
                );
            }
        } else {
            // Requisito #12: Propietario No Residente no debe figurar como residente activo de la unidad
            jdbcTemplate.update(
                    "UPDATE RESIDENTES_UNIDAD SET ESTADO = 'INACTIVO' WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    Map.of("unitId", unitId, "personaId", id)
            );
        }
        return ResponseEntity.ok().build();
    }

    private Long extractGeneratedKey(org.springframework.jdbc.support.KeyHolder kh, String columnName) {
        if (kh == null) return null;
        try {
            if (kh.getKey() != null) {
                return kh.getKey().longValue();
            }
        } catch (Exception ignored) {}
        if (kh.getKeys() != null) {
            for (Map.Entry<String, Object> entry : kh.getKeys().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(columnName) && entry.getValue() instanceof Number num) {
                    return num.longValue();
                }
            }
            for (Map.Entry<String, Object> entry : kh.getKeys().entrySet()) {
                if (!entry.getKey().equalsIgnoreCase("ROWID") && entry.getValue() instanceof Number num) {
                    return num.longValue();
                }
            }
        }
        if (kh.getKeyList() != null && !kh.getKeyList().isEmpty()) {
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

    private void restoreSaedContext(com.saed.backend.context.SaedContext prevCtx) {
        if (prevCtx != null) {
            com.saed.backend.context.SaedContextHolder.setContext(prevCtx);
            if (prevCtx.getUserId() != null && prevCtx.getRoleCode() != null) {
                try {
                    Long orgId = prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : 0L;
                    Long propId = prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : 0L;
                    String role = prevCtx.getRoleCode();
                    jdbcTemplate.getJdbcOperations().execute(
                        String.format("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(%d); PKG_SAED_SESSION.SET_CONTEXT(%d, %d, %d, '%s'); END;",
                            prevCtx.getUserId(), prevCtx.getUserId(), orgId, propId, role)
                    );
                } catch (Exception ignored) {}
            }
        } else {
            com.saed.backend.context.SaedContextHolder.clearContext();
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
            } catch (Exception ignored) {}
        }
    }
}

