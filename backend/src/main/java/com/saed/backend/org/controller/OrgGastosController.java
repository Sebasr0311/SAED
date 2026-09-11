package com.saed.backend.org.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.GastoResponseDTO;
import com.saed.backend.finanzas.dto.GastoSoporteHistorialDTO;
import com.saed.backend.finanzas.dto.OrgGastosConsolidadoDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Tag(name = "Organization Gastos", description = "Auditoria y revision ejecutiva de gastos de las propiedades de la Organizacion")
@RestController
@RequestMapping("/api/v1/org/gastos")
@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
public class OrgGastosController {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;

    public OrgGastosController(NamedParameterJdbcTemplate jdbcTemplate, FileStorageService fileStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public ApiResponse<OrgGastosConsolidadoDTO> getGastosConsolidados(
            @RequestParam(value = "idPropiedad", required = false) Long idPropiedad,
            @RequestParam(value = "fechaInicio", required = false) String fechaInicio,
            @RequestParam(value = "fechaFin", required = false) String fechaFin,
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "conSoporte", required = false) Boolean conSoporte) {

        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        String role = ctx != null ? ctx.getRoleCode() : "";

        if (!"SUPERADMIN".equalsIgnoreCase(role) && orgId == null) {
            throw new AccessDeniedException("No se encontro organizacion en el contexto activo");
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder filterSql = new StringBuilder(" WHERE 1=1");

        if (!"SUPERADMIN".equalsIgnoreCase(role)) {
            filterSql.append(" AND p_prop.ID_ORGANIZACION = :orgId");
            params.addValue("orgId", orgId);
        }

        if (idPropiedad != null) {
            filterSql.append(" AND g.ID_PROPIEDAD = :idPropiedad");
            params.addValue("idPropiedad", idPropiedad);
        }
        if (categoria != null && !categoria.isBlank()) {
            filterSql.append(" AND UPPER(g.CATEGORIA) = UPPER(:categoria)");
            params.addValue("categoria", categoria.trim());
        }
        if (estado != null && !estado.isBlank()) {
            filterSql.append(" AND UPPER(g.ESTADO) = UPPER(:estado)");
            params.addValue("estado", estado.trim());
        }
        if (Boolean.TRUE.equals(conSoporte)) {
            filterSql.append(" AND g.FACTURA_SOPORTE_URL IS NOT NULL");
        } else if (Boolean.FALSE.equals(conSoporte)) {
            filterSql.append(" AND g.FACTURA_SOPORTE_URL IS NULL");
        }
        if (fechaInicio != null && !fechaInicio.isBlank()) {
            filterSql.append(" AND g.FECHA_GASTO >= TO_DATE(:fechaInicio, 'YYYY-MM-DD')");
            params.addValue("fechaInicio", fechaInicio.trim());
        }
        if (fechaFin != null && !fechaFin.isBlank()) {
            filterSql.append(" AND g.FECHA_GASTO <= TO_DATE(:fechaFin, 'YYYY-MM-DD')");
            params.addValue("fechaFin", fechaFin.trim());
        }

        OrgGastosConsolidadoDTO consolidado = new OrgGastosConsolidadoDTO();

        // 1. Resumen de Totales y KPIs
        String kpiSql = """
            SELECT NVL(SUM(g.MONTO), 0) AS TOTAL_GASTADO,
                   COUNT(g.ID_GASTO) AS CANTIDAD_GASTOS,
                   COUNT(CASE WHEN g.FACTURA_SOPORTE_URL IS NOT NULL THEN 1 END) AS CON_SOPORTE,
                   COUNT(CASE WHEN g.FACTURA_SOPORTE_URL IS NULL THEN 1 END) AS SIN_SOPORTE
            FROM GASTOS g
            JOIN PROPIEDADES p_prop ON g.ID_PROPIEDAD = p_prop.ID_PROPIEDAD
        """ + filterSql;

        try {
            jdbcTemplate.query(kpiSql, params, rs -> {
                consolidado.setTotalGastado(rs.getBigDecimal("TOTAL_GASTADO"));
                consolidado.setCantidadGastos(rs.getLong("CANTIDAD_GASTOS"));
                consolidado.setConSoporte(rs.getLong("CON_SOPORTE"));
                consolidado.setSinSoporte(rs.getLong("SIN_SOPORTE"));
            });
        } catch (Exception e) {
            consolidado.setTotalGastado(BigDecimal.ZERO);
            consolidado.setCantidadGastos(0);
            consolidado.setConSoporte(0);
            consolidado.setSinSoporte(0);
        }

        // 2. Desglose por Categoria
        String catSql = """
            SELECT g.CATEGORIA, SUM(g.MONTO) AS TOTAL_CAT
            FROM GASTOS g
            JOIN PROPIEDADES p_prop ON g.ID_PROPIEDAD = p_prop.ID_PROPIEDAD
        """ + filterSql + " GROUP BY g.CATEGORIA ORDER BY TOTAL_CAT DESC";

        Map<String, BigDecimal> desgloseCat = new HashMap<>();
        try {
            jdbcTemplate.query(catSql, params, (rs, rowNum) -> {
                desgloseCat.put(rs.getString("CATEGORIA"), rs.getBigDecimal("TOTAL_CAT"));
                return null;
            });
        } catch (Exception ignored) {}
        consolidado.setDesgloseCategorias(desgloseCat);

        // 3. Desglose por Propiedad
        String propSql = """
            SELECT p_prop.ID_PROPIEDAD, p_prop.NOMBRE AS NOMBRE_PROPIEDAD,
                   COUNT(g.ID_GASTO) AS CANTIDAD, NVL(SUM(g.MONTO), 0) AS TOTAL_PROP
            FROM GASTOS g
            JOIN PROPIEDADES p_prop ON g.ID_PROPIEDAD = p_prop.ID_PROPIEDAD
        """ + filterSql + " GROUP BY p_prop.ID_PROPIEDAD, p_prop.NOMBRE ORDER BY TOTAL_PROP DESC";

        List<Map<String, Object>> desgloseProp = new ArrayList<>();
        try {
            jdbcTemplate.query(propSql, params, (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("idPropiedad", rs.getLong("ID_PROPIEDAD"));
                m.put("nombrePropiedad", rs.getString("NOMBRE_PROPIEDAD"));
                m.put("cantidadGastos", rs.getLong("CANTIDAD"));
                m.put("totalGastado", rs.getBigDecimal("TOTAL_PROP"));
                desgloseProp.add(m);
                return null;
            });
        } catch (Exception ignored) {}
        consolidado.setDesglosePropiedades(desgloseProp);

        // 4. Lista detallada de gastos
        String listSql = """
            SELECT g.ID_GASTO, g.ID_PROPIEDAD, p_prop.NOMBRE AS NOMBRE_PROPIEDAD,
                   g.ID_PRESUPUESTO, pr.RUBRO AS RUBRO_PRESUPUESTO,
                   g.CATEGORIA, g.BENEFICIARIO, g.PROVEEDOR_NIT, g.JUSTIFICACION,
                   g.MONTO, TO_CHAR(g.FECHA_GASTO, 'YYYY-MM-DD') AS FECHA_GASTO,
                   g.FACTURA_SOPORTE_URL, g.ARCHIVO_NOMBRE_ORIG, g.ARCHIVO_MIME_TYPE,
                   g.ARCHIVO_TAMANO_BYTES, g.ARCHIVO_SHA256,
                   g.METODO_PAGO, g.ESTADO, g.REGISTRADO_POR,
                   NVL(per.PRIMER_NOMBRE || ' ' || per.PRIMER_APELLIDO, u.EMAIL) AS REGISTRADO_POR_NOMBRE,
                   g.MODIFICADO_POR,
                   NVL(per_mod.PRIMER_NOMBRE || ' ' || per_mod.PRIMER_APELLIDO, u_mod.EMAIL) AS MODIFICADO_POR_NOMBRE,
                   TO_CHAR(g.FECHA_MODIFICACION, 'YYYY-MM-DD"T"HH24:MI:SS') AS FECHA_MODIFICACION
            FROM GASTOS g
            JOIN PROPIEDADES p_prop ON g.ID_PROPIEDAD = p_prop.ID_PROPIEDAD
            LEFT JOIN PRESUPUESTOS pr ON g.ID_PRESUPUESTO = pr.ID_PRESUPUESTO
            LEFT JOIN USUARIOS u ON g.REGISTRADO_POR = u.ID_USUARIO
            LEFT JOIN PERSONAS per ON u.ID_PERSONA = per.ID_PERSONA
            LEFT JOIN USUARIOS u_mod ON g.MODIFICADO_POR = u_mod.ID_USUARIO
            LEFT JOIN PERSONAS per_mod ON u_mod.ID_PERSONA = per_mod.ID_PERSONA
        """ + filterSql + " ORDER BY g.FECHA_GASTO DESC, g.ID_GASTO DESC";

        List<GastoResponseDTO> gastos = jdbcTemplate.query(listSql, params, (rs, rowNum) -> mapGastoRow(rs));
        consolidado.setGastos(gastos);

        return ApiResponse.success(consolidado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GastoResponseDTO>> getDetalleGasto(@PathVariable Long id) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        String role = ctx != null ? ctx.getRoleCode() : "";

        String sql = """
            SELECT g.ID_GASTO, g.ID_PROPIEDAD, p_prop.NOMBRE AS NOMBRE_PROPIEDAD,
                   g.ID_PRESUPUESTO, pr.RUBRO AS RUBRO_PRESUPUESTO,
                   g.CATEGORIA, g.BENEFICIARIO, g.PROVEEDOR_NIT, g.JUSTIFICACION,
                   g.MONTO, TO_CHAR(g.FECHA_GASTO, 'YYYY-MM-DD') AS FECHA_GASTO,
                   g.FACTURA_SOPORTE_URL, g.ARCHIVO_NOMBRE_ORIG, g.ARCHIVO_MIME_TYPE,
                   g.ARCHIVO_TAMANO_BYTES, g.ARCHIVO_SHA256,
                   g.METODO_PAGO, g.ESTADO, g.REGISTRADO_POR,
                   NVL(per.PRIMER_NOMBRE || ' ' || per.PRIMER_APELLIDO, u.EMAIL) AS REGISTRADO_POR_NOMBRE,
                   g.MODIFICADO_POR,
                   NVL(per_mod.PRIMER_NOMBRE || ' ' || per_mod.PRIMER_APELLIDO, u_mod.EMAIL) AS MODIFICADO_POR_NOMBRE,
                   TO_CHAR(g.FECHA_MODIFICACION, 'YYYY-MM-DD"T"HH24:MI:SS') AS FECHA_MODIFICACION
            FROM GASTOS g
            JOIN PROPIEDADES p_prop ON g.ID_PROPIEDAD = p_prop.ID_PROPIEDAD
            LEFT JOIN PRESUPUESTOS pr ON g.ID_PRESUPUESTO = pr.ID_PRESUPUESTO
            LEFT JOIN USUARIOS u ON g.REGISTRADO_POR = u.ID_USUARIO
            LEFT JOIN PERSONAS per ON u.ID_PERSONA = per.ID_PERSONA
            LEFT JOIN USUARIOS u_mod ON g.MODIFICADO_POR = u_mod.ID_USUARIO
            LEFT JOIN PERSONAS per_mod ON u_mod.ID_PERSONA = per_mod.ID_PERSONA
            WHERE g.ID_GASTO = :id
        """;

        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        if (!"SUPERADMIN".equalsIgnoreCase(role)) {
            sql += " AND p_prop.ID_ORGANIZACION = :orgId";
            params.addValue("orgId", orgId);
        }

        List<GastoResponseDTO> results = jdbcTemplate.query(sql, params, (rs, rowNum) -> mapGastoRow(rs));
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Gasto no encontrado en las propiedades de su organizacion"));
        }

        GastoResponseDTO dto = results.get(0);

        String histSql = """
            SELECT h.ID_HISTORIAL, h.ID_GASTO, h.FACTURA_SOPORTE_URL,
                   h.ARCHIVO_NOMBRE_ORIG, h.ARCHIVO_MIME_TYPE, h.ARCHIVO_TAMANO_BYTES,
                   h.ARCHIVO_SHA256, h.REEMPLAZADO_POR,
                   NVL(per.PRIMER_NOMBRE || ' ' || per.PRIMER_APELLIDO, u.EMAIL) AS REEMPLAZADO_POR_NOMBRE,
                   h.FECHA_REEMPLAZO, h.MOTIVO_REEMPLAZO
            FROM GASTOS_SOPORTES_HISTORIAL h
            LEFT JOIN USUARIOS u ON h.REEMPLAZADO_POR = u.ID_USUARIO
            LEFT JOIN PERSONAS per ON u.ID_PERSONA = per.ID_PERSONA
            WHERE h.ID_GASTO = :id
            ORDER BY h.FECHA_REEMPLAZO DESC
        """;
        List<GastoSoporteHistorialDTO> historial = jdbcTemplate.query(histSql, new MapSqlParameterSource("id", id), (rs, rowNum) -> {
            GastoSoporteHistorialDTO h = new GastoSoporteHistorialDTO();
            h.setIdHistorial(rs.getLong("ID_HISTORIAL"));
            h.setIdGasto(rs.getLong("ID_GASTO"));
            h.setFacturaSoporteUrl(rs.getString("FACTURA_SOPORTE_URL"));
            h.setArchivoNombreOrig(rs.getString("ARCHIVO_NOMBRE_ORIG"));
            h.setArchivoMimeType(rs.getString("ARCHIVO_MIME_TYPE"));
            long tam = rs.getLong("ARCHIVO_TAMANO_BYTES");
            h.setArchivoTamanoBytes(rs.wasNull() ? null : tam);
            h.setArchivoSha256(rs.getString("ARCHIVO_SHA256"));
            long rPor = rs.getLong("REEMPLAZADO_POR");
            h.setReemplazadoPor(rs.wasNull() ? null : rPor);
            h.setReemplazadoPorNombre(rs.getString("REEMPLAZADO_POR_NOMBRE"));
            h.setMotivoReemplazo(rs.getString("MOTIVO_REEMPLAZO"));
            return h;
        });

        dto.setHistorialSoportes(historial);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{id}/soporte")
    public ResponseEntity<Resource> descargarSoporte(
            @PathVariable Long id,
            @RequestParam(value = "download", defaultValue = "false") boolean download) {

        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        String role = ctx != null ? ctx.getRoleCode() : "";

        String sql = """
            SELECT g.FACTURA_SOPORTE_URL, g.ARCHIVO_NOMBRE_ORIG, g.ARCHIVO_MIME_TYPE
            FROM GASTOS g
            JOIN PROPIEDADES p_prop ON g.ID_PROPIEDAD = p_prop.ID_PROPIEDAD
            WHERE g.ID_GASTO = :id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        if (!"SUPERADMIN".equalsIgnoreCase(role)) {
            sql += " AND p_prop.ID_ORGANIZACION = :orgId";
            params.addValue("orgId", orgId);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Map<String, Object> row = rows.get(0);
        String relativePath = (String) row.get("FACTURA_SOPORTE_URL");
        if (relativePath == null || relativePath.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Resource resource = fileStorageService.loadAsResource(relativePath);
        String filename = (String) row.getOrDefault("ARCHIVO_NOMBRE_ORIG", "soporte_gasto");
        String mimeType = (String) row.getOrDefault("ARCHIVO_MIME_TYPE", MediaType.APPLICATION_OCTET_STREAM_VALUE);

        String disposition = (download ? "attachment; filename=\"" : "inline; filename=\"") + filename + "\"";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }

    private GastoResponseDTO mapGastoRow(ResultSet rs) throws SQLException {
        GastoResponseDTO dto = new GastoResponseDTO();
        dto.setIdGasto(rs.getLong("ID_GASTO"));
        dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
        dto.setNombrePropiedad(rs.getString("NOMBRE_PROPIEDAD"));

        long idPresup = rs.getLong("ID_PRESUPUESTO");
        dto.setIdPresupuesto(rs.wasNull() ? null : idPresup);
        dto.setRubroPresupuesto(rs.getString("RUBRO_PRESUPUESTO"));

        dto.setCategoria(rs.getString("CATEGORIA"));
        dto.setBeneficiario(rs.getString("BENEFICIARIO"));
        dto.setProveedorNit(rs.getString("PROVEEDOR_NIT"));
        dto.setJustificacion(rs.getString("JUSTIFICACION"));
        dto.setMonto(rs.getBigDecimal("MONTO"));
        dto.setFechaGasto(rs.getString("FECHA_GASTO"));
        dto.setMetodoPago(rs.getString("METODO_PAGO"));
        dto.setEstado(rs.getString("ESTADO"));

        dto.setFacturaSoporteUrl(rs.getString("FACTURA_SOPORTE_URL"));
        dto.setArchivoNombreOrig(rs.getString("ARCHIVO_NOMBRE_ORIG"));
        dto.setArchivoMimeType(rs.getString("ARCHIVO_MIME_TYPE"));
        long tam = rs.getLong("ARCHIVO_TAMANO_BYTES");
        dto.setArchivoTamanoBytes(rs.wasNull() ? null : tam);
        dto.setArchivoSha256(rs.getString("ARCHIVO_SHA256"));

        long regPor = rs.getLong("REGISTRADO_POR");
        dto.setRegistradoPor(rs.wasNull() ? null : regPor);
        dto.setRegistradoPorNombre(rs.getString("REGISTRADO_POR_NOMBRE"));

        long modPor = rs.getLong("MODIFICADO_POR");
        dto.setModificadoPor(rs.wasNull() ? null : modPor);
        dto.setModificadoPorNombre(rs.getString("MODIFICADO_POR_NOMBRE"));
        dto.setFechaModificacion(rs.getString("FECHA_MODIFICACION"));

        return dto;
    }
}
