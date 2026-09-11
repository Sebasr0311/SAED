package com.saed.backend.finanzas.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.GastoRequestDTO;
import com.saed.backend.finanzas.dto.GastoResponseDTO;
import com.saed.backend.finanzas.dto.GastoSoporteHistorialDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Tag(name = "Gastos", description = "Gestion de gastos operativos y soportes documentales")
@RestController
@RequestMapping("/api/v1/gastos")
public class GastosController {

    private static final Logger log = LoggerFactory.getLogger(GastosController.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;

    public GastosController(NamedParameterJdbcTemplate jdbcTemplate, FileStorageService fileStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
    }

    // --- 1. LISTAR GASTOS ---

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
    public ApiResponse<List<GastoResponseDTO>> listar(
            @RequestParam(value = "idPresupuesto", required = false) Long idPresupuesto,
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "conSoporte", required = false) Boolean conSoporte,
            @RequestParam(value = "fechaInicio", required = false) String fechaInicio,
            @RequestParam(value = "fechaFin", required = false) String fechaFin) {

        StringBuilder sb = new StringBuilder("""
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
            LEFT JOIN PROPIEDADES p_prop ON g.ID_PROPIEDAD = p_prop.ID_PROPIEDAD
            LEFT JOIN PRESUPUESTOS pr ON g.ID_PRESUPUESTO = pr.ID_PRESUPUESTO
            LEFT JOIN USUARIOS u ON g.REGISTRADO_POR = u.ID_USUARIO
            LEFT JOIN PERSONAS per ON u.ID_PERSONA = per.ID_PERSONA
            LEFT JOIN USUARIOS u_mod ON g.MODIFICADO_POR = u_mod.ID_USUARIO
            LEFT JOIN PERSONAS per_mod ON u_mod.ID_PERSONA = per_mod.ID_PERSONA
            WHERE 1=1
        """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (idPresupuesto != null) {
            sb.append(" AND g.ID_PRESUPUESTO = :idPresupuesto");
            params.addValue("idPresupuesto", idPresupuesto);
        }
        if (categoria != null && !categoria.isBlank()) {
            sb.append(" AND UPPER(g.CATEGORIA) = UPPER(:categoria)");
            params.addValue("categoria", categoria.trim());
        }
        if (estado != null && !estado.isBlank()) {
            sb.append(" AND UPPER(g.ESTADO) = UPPER(:estado)");
            params.addValue("estado", estado.trim());
        }
        if (Boolean.TRUE.equals(conSoporte)) {
            sb.append(" AND g.FACTURA_SOPORTE_URL IS NOT NULL");
        } else if (Boolean.FALSE.equals(conSoporte)) {
            sb.append(" AND g.FACTURA_SOPORTE_URL IS NULL");
        }
        if (fechaInicio != null && !fechaInicio.isBlank()) {
            sb.append(" AND g.FECHA_GASTO >= TO_DATE(:fechaInicio, 'YYYY-MM-DD')");
            params.addValue("fechaInicio", fechaInicio.trim());
        }
        if (fechaFin != null && !fechaFin.isBlank()) {
            sb.append(" AND g.FECHA_GASTO <= TO_DATE(:fechaFin, 'YYYY-MM-DD')");
            params.addValue("fechaFin", fechaFin.trim());
        }

        sb.append(" ORDER BY g.FECHA_GASTO DESC, g.ID_GASTO DESC");

        List<GastoResponseDTO> items = jdbcTemplate.query(sb.toString(), params, (rs, rowNum) -> mapGastoRow(rs));
        return ApiResponse.success(items);
    }

    // --- 2. DETALLE DE GASTO CON HISTORIAL DE SOPORTES ---

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
    public ResponseEntity<ApiResponse<GastoResponseDTO>> detalle(@PathVariable Long id) {
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
            LEFT JOIN PROPIEDADES p_prop ON g.ID_PROPIEDAD = p_prop.ID_PROPIEDAD
            LEFT JOIN PRESUPUESTOS pr ON g.ID_PRESUPUESTO = pr.ID_PRESUPUESTO
            LEFT JOIN USUARIOS u ON g.REGISTRADO_POR = u.ID_USUARIO
            LEFT JOIN PERSONAS per ON u.ID_PERSONA = per.ID_PERSONA
            LEFT JOIN USUARIOS u_mod ON g.MODIFICADO_POR = u_mod.ID_USUARIO
            LEFT JOIN PERSONAS per_mod ON u_mod.ID_PERSONA = per_mod.ID_PERSONA
            WHERE g.ID_GASTO = :id
        """;

        List<GastoResponseDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> mapGastoRow(rs));
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Gasto no encontrado o fuera de su alcance"));
        }

        GastoResponseDTO dto = results.get(0);

        // Cargar historial de soportes reemplazados
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

    // --- 3. STREAMING / DESCARGA DE SOPORTE DOCUMENTAL ---

    @GetMapping("/{id}/soporte")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
    public ResponseEntity<Resource> descargarSoporte(
            @PathVariable Long id,
            @RequestParam(value = "download", defaultValue = "false") boolean download) {

        String sql = "SELECT FACTURA_SOPORTE_URL, ARCHIVO_NOMBRE_ORIG, ARCHIVO_MIME_TYPE FROM GASTOS WHERE ID_GASTO = :id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", id));
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

    // --- 4. CREAR GASTO (JSON) ---

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE", resource = "GASTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> crearJson(@RequestBody Map<String, Object> body) {
        return ejecutarCreacion(
                body.get("idPresupuesto") != null ? Long.valueOf(body.get("idPresupuesto").toString()) : null,
                (String) body.getOrDefault("categoria", ""),
                (String) body.getOrDefault("beneficiario", ""),
                (String) body.getOrDefault("proveedorNit", null),
                (String) body.getOrDefault("justificacion", null),
                new BigDecimal(body.getOrDefault("monto", "0").toString()),
                (String) body.getOrDefault("fechaGasto", null),
                (String) body.getOrDefault("metodoPago", "EFECTIVO"),
                (String) body.getOrDefault("estado", "REGISTRADO"),
                (String) body.getOrDefault("facturaSoporteUrl", null),
                null
        );
    }

    // --- 5. CREAR GASTO CON ARCHIVO SOPORTE (MULTIPART) ---

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE_WITH_SUPPORT", resource = "GASTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> crearMultipart(
            @RequestParam(value = "idPresupuesto", required = false) Long idPresupuesto,
            @RequestParam("categoria") String categoria,
            @RequestParam("beneficiario") String beneficiario,
            @RequestParam(value = "proveedorNit", required = false) String proveedorNit,
            @RequestParam(value = "justificacion", required = false) String justificacion,
            @RequestParam("monto") BigDecimal monto,
            @RequestParam(value = "fechaGasto", required = false) String fechaGasto,
            @RequestParam(value = "metodoPago", defaultValue = "EFECTIVO") String metodoPago,
            @RequestParam(value = "estado", defaultValue = "REGISTRADO") String estado,
            @RequestPart(value = "soporte", required = false) MultipartFile soporte) {

        return ejecutarCreacion(
                idPresupuesto,
                categoria,
                beneficiario,
                proveedorNit,
                justificacion,
                monto,
                fechaGasto,
                metodoPago,
                estado,
                null,
                soporte
        );
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> ejecutarCreacion(
            Long idPresupuesto, String categoria, String beneficiario, String proveedorNit,
            String justificacion, BigDecimal monto, String fechaGasto, String metodoPago,
            String estado, String facturaSoporteUrlManual, MultipartFile soporteFile) {

        if (categoria == null || categoria.isBlank() || beneficiario == null || beneficiario.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Categoria y beneficiario son obligatorios"));
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("El monto debe ser mayor a 0"));
        }

        String safeMetodoPago = metodoPago != null ? metodoPago.toUpperCase().trim() : "EFECTIVO";
        String safeEstado = estado != null ? estado.toUpperCase().trim() : "REGISTRADO";
        if (!Set.of("REGISTRADO", "PAGADO", "ANULADO").contains(safeEstado)) {
            safeEstado = "REGISTRADO";
        }

        String soporteUrl = facturaSoporteUrlManual;
        String origName = null;
        String mimeType = null;
        Long tamanoBytes = null;
        String sha256 = null;

        if (soporteFile != null && !soporteFile.isEmpty()) {
            FileStorageService.StoredFile stored = fileStorageService.store(soporteFile, "gastos");
            soporteUrl = stored.relativePath();
            origName = stored.originalFilename();
            mimeType = stored.mimeType();
            tamanoBytes = stored.sizeBytes();
            sha256 = stored.sha256();
        }

        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPresupuesto", idPresupuesto)
                .addValue("categoria", categoria.trim())
                .addValue("beneficiario", beneficiario.trim())
                .addValue("proveedorNit", proveedorNit != null ? proveedorNit.trim() : null)
                .addValue("justificacion", justificacion != null ? justificacion.trim() : null)
                .addValue("monto", monto)
                .addValue("metodoPago", safeMetodoPago)
                .addValue("estado", safeEstado)
                .addValue("facturaUrl", soporteUrl)
                .addValue("origName", origName)
                .addValue("mimeType", mimeType)
                .addValue("tamanoBytes", tamanoBytes)
                .addValue("sha256", sha256);

        if (fechaGasto != null && !fechaGasto.isBlank()) {
            sql = """
                INSERT INTO GASTOS (ID_PROPIEDAD, ID_PRESUPUESTO, CATEGORIA, BENEFICIARIO,
                                    PROVEEDOR_NIT, JUSTIFICACION, MONTO, FECHA_GASTO,
                                    FACTURA_SOPORTE_URL, ARCHIVO_NOMBRE_ORIG, ARCHIVO_MIME_TYPE,
                                    ARCHIVO_TAMANO_BYTES, ARCHIVO_SHA256, METODO_PAGO, ESTADO, REGISTRADO_POR)
                VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :idPresupuesto, :categoria, :beneficiario,
                        :proveedorNit, :justificacion, :monto, TO_DATE(:fechaGasto, 'YYYY-MM-DD'),
                        :facturaUrl, :origName, :mimeType, :tamanoBytes, :sha256, :metodoPago, :estado,
                        SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'))
            """;
            params.addValue("fechaGasto", fechaGasto.trim());
        } else {
            sql = """
                INSERT INTO GASTOS (ID_PROPIEDAD, ID_PRESUPUESTO, CATEGORIA, BENEFICIARIO,
                                    PROVEEDOR_NIT, JUSTIFICACION, MONTO,
                                    FACTURA_SOPORTE_URL, ARCHIVO_NOMBRE_ORIG, ARCHIVO_MIME_TYPE,
                                    ARCHIVO_TAMANO_BYTES, ARCHIVO_SHA256, METODO_PAGO, ESTADO, REGISTRADO_POR)
                VALUES (SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD'), :idPresupuesto, :categoria, :beneficiario,
                        :proveedorNit, :justificacion, :monto,
                        :facturaUrl, :origName, :mimeType, :tamanoBytes, :sha256, :metodoPago, :estado,
                        SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'))
            """;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_GASTO"});
        Long id = keyHolder.getKey().longValue();

        if (idPresupuesto != null) {
            jdbcTemplate.update(
                    "UPDATE PRESUPUESTOS SET MONTO_EJECUTADO = MONTO_EJECUTADO + :monto WHERE ID_PRESUPUESTO = :idPresupuesto",
                    new MapSqlParameterSource("idPresupuesto", idPresupuesto).addValue("monto", monto));
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("categoria", categoria);
        resp.put("beneficiario", beneficiario);
        resp.put("monto", monto);
        resp.put("facturaSoporteUrl", soporteUrl);
        resp.put("tieneSoporte", soporteUrl != null && !soporteUrl.isBlank());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
    }

    // --- 6. ADJUNTAR O REEMPLAZAR SOPORTE DOCUMENTAL CON AUDITORIA ---

    @PostMapping(value = "/{id}/soporte", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "ATTACH_SUPPORT", resource = "GASTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> adjuntarOReemplazarSoporte(
            @PathVariable Long id,
            @RequestPart("soporte") MultipartFile soporte,
            @RequestParam(value = "motivoReemplazo", required = false) String motivoReemplazo) {

        String checkSql = """
            SELECT ID_GASTO, FACTURA_SOPORTE_URL, ARCHIVO_NOMBRE_ORIG,
                   ARCHIVO_MIME_TYPE, ARCHIVO_TAMANO_BYTES, ARCHIVO_SHA256
            FROM GASTOS WHERE ID_GASTO = :id
        """;
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(checkSql, new MapSqlParameterSource("id", id));
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Gasto no encontrado"));
        }

        Map<String, Object> currentGasto = existing.get(0);
        String currentUrl = (String) currentGasto.get("FACTURA_SOPORTE_URL");

        // Si ya existia un soporte, archivarlo en historial de reemplazos para estricta auditoria
        if (currentUrl != null && !currentUrl.isBlank()) {
            String archSql = """
                INSERT INTO GASTOS_SOPORTES_HISTORIAL (
                    ID_GASTO, FACTURA_SOPORTE_URL, ARCHIVO_NOMBRE_ORIG, ARCHIVO_MIME_TYPE,
                    ARCHIVO_TAMANO_BYTES, ARCHIVO_SHA256, REEMPLAZADO_POR, MOTIVO_REEMPLAZO
                ) VALUES (
                    :idGasto, :url, :origName, :mime, :tam, :sha,
                    SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'), :motivo
                )
            """;
            MapSqlParameterSource histParams = new MapSqlParameterSource()
                    .addValue("idGasto", id)
                    .addValue("url", currentUrl)
                    .addValue("origName", currentGasto.get("ARCHIVO_NOMBRE_ORIG"))
                    .addValue("mime", currentGasto.get("ARCHIVO_MIME_TYPE"))
                    .addValue("tam", currentGasto.get("ARCHIVO_TAMANO_BYTES"))
                    .addValue("sha", currentGasto.get("ARCHIVO_SHA256"))
                    .addValue("motivo", motivoReemplazo != null ? motivoReemplazo.trim() : "Reemplazo de soporte por actualizacion");
            jdbcTemplate.update(archSql, histParams);
        }

        // Almacenar el nuevo archivo
        FileStorageService.StoredFile stored = fileStorageService.store(soporte, "gastos");

        String updateSql = """
            UPDATE GASTOS SET
                FACTURA_SOPORTE_URL = :facturaUrl,
                ARCHIVO_NOMBRE_ORIG = :origName,
                ARCHIVO_MIME_TYPE = :mimeType,
                ARCHIVO_TAMANO_BYTES = :tamanoBytes,
                ARCHIVO_SHA256 = :sha256,
                FECHA_MODIFICACION = SYSTIMESTAMP,
                MODIFICADO_POR = SYS_CONTEXT('SAED_CTX', 'ID_USUARIO')
            WHERE ID_GASTO = :id
        """;
        MapSqlParameterSource updateParams = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("facturaUrl", stored.relativePath())
                .addValue("origName", stored.originalFilename())
                .addValue("mimeType", stored.mimeType())
                .addValue("tamanoBytes", stored.sizeBytes())
                .addValue("sha256", stored.sha256());

        jdbcTemplate.update(updateSql, updateParams);

        Map<String, Object> resp = new HashMap<>();
        resp.put("idGasto", id);
        resp.put("facturaSoporteUrl", stored.relativePath());
        resp.put("archivoNombreOrig", stored.originalFilename());
        resp.put("archivoTamanoBytes", stored.sizeBytes());
        resp.put("sha256", stored.sha256());

        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    // --- 7. ACTUALIZAR GASTO ---

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE", resource = "GASTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<String>> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> prevList = jdbcTemplate.queryForList(
                "SELECT ID_PRESUPUESTO, MONTO FROM GASTOS WHERE ID_GASTO = :id",
                new MapSqlParameterSource("id", id));
        if (prevList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Gasto no encontrado"));
        }

        Map<String, Object> prev = prevList.get(0);
        Number prevPresupuesto = (Number) prev.get("ID_PRESUPUESTO");
        Number prevMontoNum = (Number) prev.get("MONTO");
        BigDecimal prevMonto = prevMontoNum != null ? new BigDecimal(prevMontoNum.toString()) : BigDecimal.ZERO;

        String categoria = (String) body.getOrDefault("categoria", null);
        String beneficiario = (String) body.getOrDefault("beneficiario", null);
        String proveedorNit = (String) body.getOrDefault("proveedorNit", null);
        String justificacion = (String) body.getOrDefault("justificacion", null);
        Number montoNum = (Number) body.getOrDefault("monto", null);
        BigDecimal newMonto = montoNum != null ? new BigDecimal(montoNum.toString()) : null;
        String metodoPago = body.get("metodoPago") != null ? ((String) body.get("metodoPago")).toUpperCase() : null;
        String estado = body.get("estado") != null ? ((String) body.get("estado")).toUpperCase() : null;
        String fechaGasto = (String) body.getOrDefault("fechaGasto", null);
        Number newPresupuestoNum = (Number) body.getOrDefault("idPresupuesto", null);
        Long newPresupuesto = newPresupuestoNum != null ? newPresupuestoNum.longValue() : null;

        if (estado != null && !Set.of("REGISTRADO", "PAGADO", "ANULADO").contains(estado)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Estado no valido. Permitidos: REGISTRADO, PAGADO, ANULADO"));
        }

        StringBuilder sb = new StringBuilder("UPDATE GASTOS SET ");
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        boolean first = true;

        if (categoria != null) {
            sb.append(first ? "" : ", ").append("CATEGORIA = :categoria");
            params.addValue("categoria", categoria.trim());
            first = false;
        }
        if (beneficiario != null) {
            sb.append(first ? "" : ", ").append("BENEFICIARIO = :beneficiario");
            params.addValue("beneficiario", beneficiario.trim());
            first = false;
        }
        if (proveedorNit != null) {
            sb.append(first ? "" : ", ").append("PROVEEDOR_NIT = :proveedorNit");
            params.addValue("proveedorNit", proveedorNit.trim());
            first = false;
        }
        if (justificacion != null) {
            sb.append(first ? "" : ", ").append("JUSTIFICACION = :justificacion");
            params.addValue("justificacion", justificacion.trim());
            first = false;
        }
        if (newMonto != null) {
            sb.append(first ? "" : ", ").append("MONTO = :monto");
            params.addValue("monto", newMonto);
            first = false;
        }
        if (metodoPago != null) {
            sb.append(first ? "" : ", ").append("METODO_PAGO = :metodoPago");
            params.addValue("metodoPago", metodoPago.trim());
            first = false;
        }
        if (estado != null) {
            sb.append(first ? "" : ", ").append("ESTADO = :estado");
            params.addValue("estado", estado.trim());
            first = false;
        }
        if (fechaGasto != null && !fechaGasto.isBlank()) {
            sb.append(first ? "" : ", ").append("FECHA_GASTO = TO_DATE(:fechaGasto, 'YYYY-MM-DD')");
            params.addValue("fechaGasto", fechaGasto.trim());
            first = false;
        }
        if (body.containsKey("idPresupuesto")) {
            sb.append(first ? "" : ", ").append("ID_PRESUPUESTO = :idPresupuesto");
            params.addValue("idPresupuesto", newPresupuesto);
            first = false;
        }

        if (first) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Ningun campo para actualizar"));
        }

        sb.append(", FECHA_MODIFICACION = SYSTIMESTAMP, MODIFICADO_POR = SYS_CONTEXT('SAED_CTX', 'ID_USUARIO')");
        sb.append(" WHERE ID_GASTO = :id");

        int rows = jdbcTemplate.update(sb.toString(), params);
        if (rows == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Gasto no encontrado"));

        // Conciliar monto ejecutado en PRESUPUESTOS si el monto o el presupuesto cambiaron
        if (newMonto != null && prevPresupuesto != null && (newPresupuesto == null || newPresupuesto.equals(prevPresupuesto.longValue()))) {
            BigDecimal diff = newMonto.subtract(prevMonto);
            jdbcTemplate.update(
                    "UPDATE PRESUPUESTOS SET MONTO_EJECUTADO = MONTO_EJECUTADO + :diff WHERE ID_PRESUPUESTO = :pId",
                    new MapSqlParameterSource("diff", diff).addValue("pId", prevPresupuesto));
        }

        return ResponseEntity.ok(ApiResponse.success("OK"));
    }

    // --- 8. ELIMINAR GASTO ---

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "DELETE", resource = "GASTO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<String>> eliminar(@PathVariable Long id) {
        List<Map<String, Object>> gastos = jdbcTemplate.queryForList(
                "SELECT ID_PRESUPUESTO, MONTO, FACTURA_SOPORTE_URL FROM GASTOS WHERE ID_GASTO = :id",
                new MapSqlParameterSource("id", id));

        if (gastos.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Gasto no encontrado"));
        }

        Map<String, Object> gasto = gastos.get(0);
        Number idPresupuesto = (Number) gasto.get("ID_PRESUPUESTO");
        Number monto = (Number) gasto.get("MONTO");
        String soporteUrl = (String) gasto.get("FACTURA_SOPORTE_URL");

        jdbcTemplate.update("DELETE FROM GASTOS WHERE ID_GASTO = :id", new MapSqlParameterSource("id", id));

        if (idPresupuesto != null && monto != null) {
            jdbcTemplate.update(
                    "UPDATE PRESUPUESTOS SET MONTO_EJECUTADO = MONTO_EJECUTADO - :monto WHERE ID_PRESUPUESTO = :idPresupuesto",
                    new MapSqlParameterSource("idPresupuesto", idPresupuesto).addValue("monto", monto));
        }

        if (soporteUrl != null && !soporteUrl.isBlank()) {
            fileStorageService.delete(soporteUrl);
        }

        return ResponseEntity.ok(ApiResponse.success("Gasto eliminado exitosamente"));
    }

    // --- HELPER DE MAPEO ---

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
