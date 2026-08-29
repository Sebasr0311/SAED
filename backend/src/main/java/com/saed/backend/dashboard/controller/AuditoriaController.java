package com.saed.backend.dashboard.controller;

import com.saed.backend.common.dto.ApiResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * AuditoriaController — lectura de AUDITORIA_LOG (append-only).
 *
 * Solo lectura con filtros: tabla, acción, rango de fechas, usuario.
 * No hay escritura desde este controller; los registros los crean los
 * triggers TRG_AUDIT_* y el GlobalExceptionHandler.
 *
 * Contrato:
 *   GET /api/v1/audit                     — lista con filtros opcionales
 *   GET /api/v1/audit/stats               — estadísticas (conteo por tabla/acción)
 *   GET /api/v1/audit/{id}                — detalle de un registro
 */
@Tag(name = "Auditoría", description = "Lectura de registros de auditoría (append-only)")
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD')")
public class AuditoriaController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuditoriaController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar(
            @RequestParam(value = "tabla", required = false) String tabla,
            @RequestParam(value = "accion", required = false) String accion,
            @RequestParam(value = "usuario", required = false) String usuario,
            @RequestParam(value = "desde", required = false) String desde,
            @RequestParam(value = "hasta", required = false) String hasta,
            @RequestParam(value = "limite", defaultValue = "100") int limite) {

        StringBuilder sb = new StringBuilder("SELECT ID_LOG, ENTIDAD, ACCION, ID_ENTIDAD_AFECTADA, " +
                "ID_USUARIO, FECHA_HORA, ESTADO_ANTERIOR, ESTADO_NUEVO, " +
                "ID_ORGANIZACION, ID_PROPIEDAD " +
                "FROM AUDITORIA_LOG WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (tabla != null && !tabla.isBlank()) {
            sb.append(" AND ENTIDAD = :tabla");
            params.addValue("tabla", tabla.toUpperCase());
        }
        if (accion != null && !accion.isBlank()) {
            sb.append(" AND ACCION = :accion");
            params.addValue("accion", accion.toUpperCase());
        }
        if (usuario != null && !usuario.isBlank()) {
            sb.append(" AND ID_USUARIO = :usuario");
            params.addValue("usuario", usuario);
        }
        if (desde != null && !desde.isBlank()) {
            sb.append(" AND FECHA_HORA >= TO_DATE(:desde, 'YYYY-MM-DD')");
            params.addValue("desde", desde);
        }
        if (hasta != null && !hasta.isBlank()) {
            sb.append(" AND FECHA_HORA <= TO_DATE(:hasta, 'YYYY-MM-DD') + 1");
            params.addValue("hasta", hasta);
        }

        sb.append(" ORDER BY FECHA_HORA DESC FETCH FIRST :limite ROWS ONLY");
        params.addValue("limite", limite);

        List<Map<String, Object>> items = jdbcTemplate.queryForList(sb.toString(), params);
        return ApiResponse.success(items);
    }

    @GetMapping("/stats")
    public ApiResponse<List<Map<String, Object>>> estadisticas() {
        String sql = "SELECT ENTIDAD, ACCION, COUNT(*) AS TOTAL, " +
                     "MIN(FECHA_HORA) AS PRIMERA, MAX(FECHA_HORA) AS ULTIMA " +
                     "FROM AUDITORIA_LOG " +
                     "GROUP BY ENTIDAD, ACCION " +
                     "ORDER BY TOTAL DESC";
        return ApiResponse.success(jdbcTemplate.queryForList(sql, new MapSqlParameterSource()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detalle(@PathVariable Long id) {
        String sql = "SELECT * FROM AUDITORIA_LOG WHERE ID_LOG = :id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) return ApiResponse.error("Registro no encontrado");
        return ApiResponse.success(rows.get(0));
    }
}
