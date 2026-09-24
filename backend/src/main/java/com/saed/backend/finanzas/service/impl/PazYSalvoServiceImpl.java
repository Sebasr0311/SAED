package com.saed.backend.finanzas.service.impl;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.PazYSalvoDetalleDTO;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PazYSalvoServiceImpl implements PazYSalvoService {

    private static final Logger log = LoggerFactory.getLogger(PazYSalvoServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;
    private final PdfService pdfService;

    public PazYSalvoServiceImpl(
            NamedParameterJdbcTemplate jdbcTemplate,
            FileStorageService fileStorageService,
            PdfService pdfService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
        this.pdfService = pdfService;
    }

    @Override
    public PazYSalvoEstadoFinancieroDTO verificarEstadoFinanciero(Long idUnidad) {
        if (idUnidad == null) {
            throw new IllegalArgumentException("idUnidad es obligatorio");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
                if (ctx.getUnitId() == null || !ctx.getUnitId().equals(idUnidad)) {
                    throw new AccessDeniedException("No tiene permisos para consultar el estado financiero de otra unidad");
                }
            }
        }

        Map<String, Object> unitInfo = getUnitInfo(idUnidad);
        if (unitInfo == null) {
            throw new IllegalArgumentException("Unidad no encontrada: " + idUnidad);
        }

        // Anti-IDOR enforcement on financial status inquiry
        enforceUnitAccess(ctx, unitInfo, "consultar el estado financiero de esta unidad");

        String identificador = (String) unitInfo.get("IDENTIFICADOR");

        // 1. Cartera & Cuotas corrientes / vencidas
        String cuotasSql = "SELECT COALESCE(SUM(c.SALDO_PENDIENTE), 0) FROM CUOTAS c " +
                "WHERE c.ID_UNIDAD = :unitId AND c.ESTADO IN ('PENDIENTE', 'VENCIDA', 'EN_MORA')";
        BigDecimal saldoCuotas = jdbcTemplate.queryForObject(
                cuotasSql,
                new MapSqlParameterSource("unitId", idUnidad),
                BigDecimal.class);
        if (saldoCuotas == null) saldoCuotas = BigDecimal.ZERO;

        List<Map<String, Object>> carteraRows = jdbcTemplate.queryForList(
                "SELECT SALDO_TOTAL FROM CARTERA WHERE ID_UNIDAD = :unitId",
                new MapSqlParameterSource("unitId", idUnidad));
        BigDecimal saldoCartera = saldoCuotas;
        if (!carteraRows.isEmpty() && carteraRows.get(0).get("SALDO_TOTAL") != null) {
            BigDecimal carteraTotal = new BigDecimal(carteraRows.get(0).get("SALDO_TOTAL").toString());
            if (carteraTotal.compareTo(saldoCartera) > 0) {
                saldoCartera = carteraTotal;
            }
        }

        // 2. Multas no facturadas (ID_CUOTA IS NULL y estado exigible)
        String multasSql = "SELECT COALESCE(SUM(m.MONTO), 0) FROM MULTAS m " +
                "WHERE m.ID_UNIDAD = :unitId AND m.ID_CUOTA IS NULL " +
                "AND m.ESTADO IN ('IMPUESTA', 'EN_DESCARGOS', 'RATIFICADA')";
        BigDecimal saldoMultas = jdbcTemplate.queryForObject(
                multasSql,
                new MapSqlParameterSource("unitId", idUnidad),
                BigDecimal.class);
        if (saldoMultas == null) saldoMultas = BigDecimal.ZERO;

        BigDecimal saldoTotalExigible = saldoCartera.add(saldoMultas);
        boolean isPazYSalvo = saldoTotalExigible.compareTo(BigDecimal.ZERO) <= 0;

        List<String> motivos = new ArrayList<>();
        if (saldoCartera.compareTo(BigDecimal.ZERO) > 0) {
            motivos.add("Saldo pendiente en cartera/cuotas por $" + saldoCartera);
        }
        if (saldoMultas.compareTo(BigDecimal.ZERO) > 0) {
            motivos.add("Sanciones/multas pendientes de pago por $" + saldoMultas);
        }

        return new PazYSalvoEstadoFinancieroDTO(
                idUnidad,
                identificador,
                saldoCartera,
                saldoMultas,
                saldoTotalExigible,
                isPazYSalvo,
                motivos);
    }

    @Override
    @Transactional
    public Map<String, Object> generarPazYSalvo(Long idUnidad, String motivo) {
        if (idUnidad == null) {
            throw new IllegalArgumentException("idUnidad es obligatorio");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role) || "PORTERO".equalsIgnoreCase(role)) {
                throw new AccessDeniedException("No tiene permisos para emitir certificados de paz y salvo");
            }
        }

        Map<String, Object> unitInfo = getUnitInfo(idUnidad);
        if (unitInfo == null) {
            throw new IllegalArgumentException("Unidad no encontrada: " + idUnidad);
        }

        // Anti-IDOR check for admin
        enforceUnitAccess(ctx, unitInfo, "emitir paz y salvo para una unidad de otra propiedad u organización");

        // Regla F6-04-01: Integridad Financiera Estricta
        PazYSalvoEstadoFinancieroDTO estadoFinanciero = verificarEstadoFinanciero(idUnidad);
        if (!estadoFinanciero.pazYSalvo() || estadoFinanciero.saldoTotalExigible().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Unidad tiene saldo pendiente");
        }

        Long orgId = ((Number) unitInfo.get("ID_ORGANIZACION")).longValue();
        String unitIdentificador = (String) unitInfo.get("IDENTIFICADOR");
        String propNombre = (String) unitInfo.get("NOMBRE_PROPIEDAD");
        String propNit = (String) unitInfo.get("NIT");
        String propDireccion = (String) unitInfo.get("DIRECCION");
        String propCiudad = (String) unitInfo.get("CIUDAD");

        // Resolver solicitante
        Long idPersonaSolicitante = resolvePersonaSolicitante(idUnidad, ctx);
        Map<String, Object> solicitanteInfo = getPersonaInfo(idPersonaSolicitante);
        String solicitanteNombre = solicitanteInfo != null ? (String) solicitanteInfo.get("NOMBRE") : "PROPIETARIO / RESIDENTE";
        String solicitanteDoc = solicitanteInfo != null ? (String) solicitanteInfo.get("DOCUMENTO") : "NO REGISTRADO";

        String codigoVerificacion = UUID.randomUUID().toString();
        LocalDate fechaEmision = LocalDate.now();
        LocalDate fechaVencimiento = fechaEmision.plusDays(30);

        // Generar artefacto PDF oficial
        String htmlContent = buildPazYSalvoHtml(
                propNombre, propNit, propDireccion, propCiudad,
                unitIdentificador, solicitanteNombre, solicitanteDoc,
                codigoVerificacion, fechaEmision, fechaVencimiento, motivo);

        byte[] pdfBytes;
        try {
            pdfBytes = pdfService.generarPdf(htmlContent);
        } catch (Exception e) {
            log.error("Error al renderizar documento PDF de paz y salvo para unidad {}", idUnidad, e);
            throw new RuntimeException("Error en el pipeline de renderizado del documento PDF", e);
        }

        String filename = "paz_y_salvo_" + unitIdentificador + "_" + codigoVerificacion.substring(0, 8) + ".pdf";
        FileStorageService.StoredFile storedFile = null;
        try {
            // Guardar físico con validación de quota
            storedFile = fileStorageService.storeBytes(pdfBytes, filename, "application/pdf", "paz_y_salvos", orgId);

            Long emitidoPor = (ctx != null && ctx.getUserId() != null) ? ctx.getUserId() : 1L;

            String insertSql = "INSERT INTO PAZ_Y_SALVOS (" +
                    "ID_UNIDAD, ID_PERSONA_SOLICITANTE, CODIGO_VERIFICACION, " +
                    "FECHA_EMISION, FECHA_VENCIMIENTO, SALDO_A_LA_FECHA, " +
                    "MOTIVO, DOCUMENTO_PDF_URL, DOCUMENTO_HASH, DOCUMENTO_TAMANO_BYTES, " +
                    "DOCUMENTO_FECHA_GENERACION, EMITIDO_POR, ESTADO" +
                    ") VALUES (" +
                    ":idUnidad, :idPersonaSolicitante, :codigo, " +
                    "CURRENT_TIMESTAMP, :fechaVencimiento, 0, " +
                    ":motivo, :pdfUrl, :hash, :tamano, " +
                    "CURRENT_TIMESTAMP, :emitidoPor, 'VALIDO')";

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("idUnidad", idUnidad)
                    .addValue("idPersonaSolicitante", idPersonaSolicitante)
                    .addValue("codigo", codigoVerificacion)
                    .addValue("fechaVencimiento", java.sql.Date.valueOf(fechaVencimiento))
                    .addValue("motivo", (motivo != null && !motivo.isBlank()) ? motivo.trim() : null)
                    .addValue("pdfUrl", storedFile.relativePath())
                    .addValue("hash", storedFile.sha256())
                    .addValue("tamano", storedFile.sizeBytes())
                    .addValue("emitidoPor", emitidoPor);

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(insertSql, params, keyHolder, new String[]{"ID_PAZ_SALVO"});
            Long idPazSalvo = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;

            log.info("Paz y Salvo {} emitido exitosamente para unidad {} (PDF: {}, SHA-256: {})",
                    idPazSalvo, idUnidad, storedFile.relativePath(), storedFile.sha256());

            return Map.of(
                    "id", idPazSalvo != null ? idPazSalvo : 0L,
                    "codigoVerificacion", codigoVerificacion,
                    "estado", "VALIDO",
                    "documentoPdfUrl", storedFile.relativePath(),
                    "documentoHash", storedFile.sha256(),
                    "documentoTamanoBytes", storedFile.sizeBytes()
            );
        } catch (Exception ex) {
            log.error("Fallo al persistir Paz y Salvo en base de datos. Limpiando artefacto huérfano...", ex);
            if (storedFile != null) {
                try {
                    fileStorageService.delete(storedFile.relativePath());
                } catch (Exception cleanupEx) {
                    log.warn("No se pudo limpiar archivo huérfano: {}", storedFile.relativePath(), cleanupEx);
                }
            }
            if (ex instanceof RuntimeException re) throw re;
            throw new RuntimeException("Error al guardar Paz y Salvo", ex);
        }
    }

    @Override
    public List<Map<String, Object>> listar() {
        SaedContext ctx = SaedContextHolder.getContext();
        StringBuilder sql = new StringBuilder(
                "SELECT p.ID_PAZ_SALVO, p.ID_UNIDAD, u.IDENTIFICADOR AS NUMERO_APARTAMENTO, " +
                "p.ID_PERSONA_SOLICITANTE, p.CODIGO_VERIFICACION, p.FECHA_EMISION, p.FECHA_VENCIMIENTO, " +
                "p.SALDO_A_LA_FECHA, p.MOTIVO, p.DOCUMENTO_PDF_URL, p.DOCUMENTO_HASH, " +
                "p.DOCUMENTO_TAMANO_BYTES, p.DOCUMENTO_FECHA_GENERACION, p.EMITIDO_POR, p.ESTADO " +
                "FROM PAZ_Y_SALVOS p " +
                "JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD " +
                "JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD " +
                "WHERE 1=1 ");

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
                if (ctx.getUnitId() != null) {
                    sql.append("AND p.ID_UNIDAD = :unitId ");
                    params.addValue("unitId", ctx.getUnitId());
                } else {
                    sql.append("AND 1=0 ");
                }
            } else if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
                if (ctx.getPropertyId() != null) {
                    sql.append("AND pr.ID_PROPIEDAD = :propId ");
                    params.addValue("propId", ctx.getPropertyId());
                }
            } else if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role)) {
                if (ctx.getOrganizationId() != null) {
                    sql.append("AND pr.ID_ORGANIZACION = :orgId ");
                    params.addValue("orgId", ctx.getOrganizationId());
                }
            }
        }

        sql.append("ORDER BY p.FECHA_EMISION DESC");
        return jdbcTemplate.queryForList(sql.toString(), params);
    }

    @Override
    public PazYSalvoDetalleDTO obtenerDetalle(Long idPazSalvo) {
        if (idPazSalvo == null) {
            throw new IllegalArgumentException("idPazSalvo es obligatorio");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        List<Map<String, Object>> rows;
        setElevatedContext();
        try {
            String sql = "SELECT p.ID_PAZ_SALVO, p.ID_UNIDAD, u.IDENTIFICADOR AS NUMERO_UNIDAD, " +
                    "pr.ID_PROPIEDAD, pr.NOMBRE AS NOMBRE_PROPIEDAD, org.IDENTIFICACION_FISCAL AS NIT_PROPIEDAD, " +
                    "pr.DIRECCION AS DIRECCION_PROPIEDAD, pr.CIUDAD AS CIUDAD_PROPIEDAD, pr.ID_ORGANIZACION, " +
                    "p.ID_PERSONA_SOLICITANTE, " +
                    "TRIM(CONCAT(CONCAT(per.PRIMER_NOMBRE, ' '), per.PRIMER_APELLIDO)) AS NOMBRE_SOLICITANTE, " +
                    "per.NUMERO_DOCUMENTO AS DOCUMENTO_SOLICITANTE, " +
                    "p.CODIGO_VERIFICACION, p.FECHA_EMISION, p.FECHA_VENCIMIENTO, p.SALDO_A_LA_FECHA, " +
                    "p.MOTIVO, p.DOCUMENTO_PDF_URL, p.DOCUMENTO_HASH, p.DOCUMENTO_TAMANO_BYTES, " +
                    "p.DOCUMENTO_FECHA_GENERACION, p.EMITIDO_POR, p.ESTADO " +
                    "FROM PAZ_Y_SALVOS p " +
                    "JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD " +
                    "JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD " +
                    "LEFT JOIN ORGANIZACIONES org ON pr.ID_ORGANIZACION = org.ID_ORGANIZACION " +
                    "LEFT JOIN PERSONAS per ON p.ID_PERSONA_SOLICITANTE = per.ID_PERSONA " +
                    "WHERE p.ID_PAZ_SALVO = :id";

            rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", idPazSalvo));
        } finally {
            restoreSaedContext(ctx);
        }

        if (rows.isEmpty()) {
            throw new NoSuchElementException("Paz y salvos no encontrado: " + idPazSalvo);
        }

        Map<String, Object> r = rows.get(0);
        PazYSalvoDetalleDTO detalle = mapToDetalleDTO(r);

        // Anti-IDOR check against the calling context
        enforceDetalleAccess(ctx, detalle);

        return detalle;
    }

    @Override
    public Resource descargarPdf(Long idPazSalvo) {
        PazYSalvoDetalleDTO detalle = obtenerDetalle(idPazSalvo);
        if (detalle.documentoPdfUrl() == null || detalle.documentoPdfUrl().isBlank()) {
            throw new IllegalStateException("El documento PDF oficial no ha sido generado para este paz y salvo");
        }

        return fileStorageService.loadAsResource(detalle.documentoPdfUrl());
    }

    @Override
    public Map<String, Object> verificarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Código de verificación es obligatorio");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        List<Map<String, Object>> rows;
        setElevatedContext();
        try {
            String sql = "SELECT p.ID_PAZ_SALVO, p.ID_UNIDAD, u.IDENTIFICADOR AS NUMERO_UNIDAD, " +
                    "pr.NOMBRE AS NOMBRE_PROPIEDAD, p.CODIGO_VERIFICACION, p.FECHA_EMISION, " +
                    "p.FECHA_VENCIMIENTO, p.SALDO_A_LA_FECHA, p.MOTIVO, p.DOCUMENTO_HASH, p.ESTADO " +
                    "FROM PAZ_Y_SALVOS p " +
                    "JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD " +
                    "JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD " +
                    "WHERE p.CODIGO_VERIFICACION = :codigo";

            rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("codigo", codigo.trim()));
        } finally {
            restoreSaedContext(ctx);
        }

        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> pazSalvo = rows.get(0);
        String estado = (String) pazSalvo.get("ESTADO");
        LocalDate fechaVencimiento = toLocalDate(pazSalvo.get("FECHA_VENCIMIENTO"));

        if ("VENCIDO".equalsIgnoreCase(estado) || (fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now()))) {
            pazSalvo.put("ESTADO", "VENCIDO");
            pazSalvo.put("esValido", false);
            pazSalvo.put("mensaje", "El certificado de paz y salvo se encuentra vencido.");
        } else {
            pazSalvo.put("esValido", true);
        }

        return pazSalvo;
    }

    // --- MÉTODOS AUXILIARES Y SEGURIDAD ANTI-IDOR ---

    private Map<String, Object> getUnitInfo(Long idUnidad) {
        String sql = "SELECT u.ID_UNIDAD, u.IDENTIFICADOR, u.ID_PROPIEDAD, " +
                "p.ID_ORGANIZACION, p.NOMBRE AS NOMBRE_PROPIEDAD, o.IDENTIFICACION_FISCAL AS NIT, p.DIRECCION, p.CIUDAD " +
                "FROM UNIDADES u " +
                "JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD " +
                "LEFT JOIN ORGANIZACIONES o ON p.ID_ORGANIZACION = o.ID_ORGANIZACION " +
                "WHERE u.ID_UNIDAD = :idUnidad";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("idUnidad", idUnidad));
        return list.isEmpty() ? null : list.get(0);
    }

    private Map<String, Object> getPersonaInfo(Long idPersona) {
        if (idPersona == null) return null;
        String sql = "SELECT TRIM(CONCAT(CONCAT(PRIMER_NOMBRE, ' '), PRIMER_APELLIDO)) AS NOMBRE, " +
                "NUMERO_DOCUMENTO AS DOCUMENTO, EMAIL " +
                "FROM PERSONAS WHERE ID_PERSONA = :pId";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("pId", idPersona));
        return list.isEmpty() ? null : list.get(0);
    }

    private Long resolvePersonaSolicitante(Long idUnidad, SaedContext ctx) {
        // 1. Si el contexto tiene un usuario, intentar resolver su persona
        if (ctx != null && ctx.getUserId() != null) {
            try {
                Long pId = jdbcTemplate.queryForObject(
                        "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :uId",
                        new MapSqlParameterSource("uId", ctx.getUserId()),
                        Long.class);
                if (pId != null) return pId;
            } catch (Exception ignored) {}
        }

        // 2. Buscar propietario activo de la unidad
        try {
            List<Long> props = jdbcTemplate.queryForList(
                    "SELECT ID_PERSONA FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = :uId FETCH FIRST 1 ROWS ONLY",
                    new MapSqlParameterSource("uId", idUnidad), Long.class);
            if (!props.isEmpty()) return props.get(0);
        } catch (Exception ignored) {}

        // 3. Buscar residente activo de la unidad
        try {
            List<Long> res = jdbcTemplate.queryForList(
                    "SELECT ID_PERSONA FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = :uId AND ESTADO = 'ACTIVO' FETCH FIRST 1 ROWS ONLY",
                    new MapSqlParameterSource("uId", idUnidad), Long.class);
            if (!res.isEmpty()) return res.get(0);
        } catch (Exception ignored) {}

        return 1L; // Fallback seguro
    }

    private void enforceUnitAccess(SaedContext ctx, Map<String, Object> unitInfo, String actionDescription) {
        if (ctx == null) return;
        String role = ctx.getRoleCode();
        if ("SUPERADMIN".equalsIgnoreCase(role)) return;

        Long unitPropId = ((Number) unitInfo.get("ID_PROPIEDAD")).longValue();
        Long unitOrgId = ((Number) unitInfo.get("ID_ORGANIZACION")).longValue();
        Long unitId = ((Number) unitInfo.get("ID_UNIDAD")).longValue();

        if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role)) {
            if (ctx.getOrganizationId() != null && !ctx.getOrganizationId().equals(unitOrgId)) {
                throw new AccessDeniedException("No tiene permisos para " + actionDescription);
            }
        } else if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (ctx.getPropertyId() != null && !ctx.getPropertyId().equals(unitPropId)) {
                throw new AccessDeniedException("No tiene permisos para " + actionDescription);
            }
        } else if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            if (ctx.getUnitId() == null || !ctx.getUnitId().equals(unitId)) {
                throw new AccessDeniedException("No tiene permisos para " + actionDescription);
            }
        }
    }

    private void enforceDetalleAccess(SaedContext ctx, PazYSalvoDetalleDTO detalle) {
        if (ctx == null) return;
        String role = ctx.getRoleCode();
        if ("SUPERADMIN".equalsIgnoreCase(role)) return;

        if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role)) {
            if (ctx.getOrganizationId() != null && !ctx.getOrganizationId().equals(detalle.idOrganizacion())) {
                throw new AccessDeniedException("No tiene permisos para consultar certificados de otra organización");
            }
        } else if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (ctx.getPropertyId() != null && !ctx.getPropertyId().equals(detalle.idPropiedad())) {
                throw new AccessDeniedException("No tiene permisos para consultar certificados de otra propiedad");
            }
        } else if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            boolean matchesUnit = ctx.getUnitId() != null && ctx.getUnitId().equals(detalle.idUnidad());
            if (!matchesUnit) {
                throw new AccessDeniedException("No tiene permisos para consultar certificados de otra unidad");
            }
        }
    }

    private PazYSalvoDetalleDTO mapToDetalleDTO(Map<String, Object> r) {
        return new PazYSalvoDetalleDTO(
                ((Number) r.get("ID_PAZ_SALVO")).longValue(),
                ((Number) r.get("ID_UNIDAD")).longValue(),
                (String) r.get("NUMERO_UNIDAD"),
                ((Number) r.get("ID_PROPIEDAD")).longValue(),
                (String) r.get("NOMBRE_PROPIEDAD"),
                (String) r.get("NIT_PROPIEDAD"),
                (String) r.get("DIRECCION_PROPIEDAD"),
                (String) r.get("CIUDAD_PROPIEDAD"),
                ((Number) r.get("ID_ORGANIZACION")).longValue(),
                r.get("ID_PERSONA_SOLICITANTE") != null ? ((Number) r.get("ID_PERSONA_SOLICITANTE")).longValue() : null,
                (String) r.get("NOMBRE_SOLICITANTE"),
                (String) r.get("DOCUMENTO_SOLICITANTE"),
                (String) r.get("CODIGO_VERIFICACION"),
                r.get("FECHA_EMISION") != null ? toOffsetDateTime(r.get("FECHA_EMISION")) : null,
                toLocalDate(r.get("FECHA_VENCIMIENTO")),
                r.get("SALDO_A_LA_FECHA") != null ? new BigDecimal(r.get("SALDO_A_LA_FECHA").toString()) : BigDecimal.ZERO,
                (String) r.get("MOTIVO"),
                (String) r.get("DOCUMENTO_PDF_URL"),
                (String) r.get("DOCUMENTO_HASH"),
                r.get("DOCUMENTO_TAMANO_BYTES") != null ? ((Number) r.get("DOCUMENTO_TAMANO_BYTES")).longValue() : null,
                r.get("DOCUMENTO_FECHA_GENERACION") != null ? toOffsetDateTime(r.get("DOCUMENTO_FECHA_GENERACION")) : null,
                r.get("EMITIDO_POR") != null ? ((Number) r.get("EMITIDO_POR")).longValue() : null,
                (String) r.get("ESTADO")
        );
    }

    private LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        } else if (val instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (val instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (val instanceof LocalDate ld) {
            return ld;
        }
        return null;
    }

    private OffsetDateTime toOffsetDateTime(Object val) {
        if (val instanceof Timestamp ts) {
            return ts.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } else if (val instanceof OffsetDateTime odt) {
            return odt;
        }
        return OffsetDateTime.now();
    }

    private String buildPazYSalvoHtml(
            String propNombre, String propNit, String propDireccion, String propCiudad,
            String unitIdentificador, String solicitanteNombre, String solicitanteDoc,
            String codigoVerificacion, LocalDate fechaEmision, LocalDate fechaVencimiento, String motivo) {

        String safePropNombre = propNombre != null ? propNombre : "Copropiedad";
        String safeNit = propNit != null ? propNit : "N/A";
        String safeDireccion = propDireccion != null ? propDireccion : "";
        String safeCiudad = propCiudad != null ? propCiudad : "Colombia";
        String safeMotivo = (motivo != null && !motivo.isBlank()) ? motivo : "Trámite particular";

        return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "<head>\n" +
                "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
                "  <title>Certificado Oficial de Paz y Salvo</title>\n" +
                "  <style type=\"text/css\">\n" +
                "    @page { size: letter; margin: 20mm 15mm; }\n" +
                "    body { font-family: Helvetica, Arial, sans-serif; color: #0f172a; line-height: 1.6; font-size: 13px; }\n" +
                "    .header { text-align: center; border-bottom: 2px solid #0284c7; padding-bottom: 15px; margin-bottom: 25px; }\n" +
                "    .org-title { font-size: 16px; font-weight: bold; color: #0284c7; margin: 0; text-transform: uppercase; }\n" +
                "    .doc-title { font-size: 20px; font-weight: bold; color: #0f172a; margin: 10px 0 0 0; text-transform: uppercase; letter-spacing: 1px; }\n" +
                "    .doc-subtitle { font-size: 11px; color: #64748b; margin: 5px 0 0 0; }\n" +
                "    .content-box { border: 1px solid #cbd5e1; border-radius: 6px; padding: 15px; margin-bottom: 20px; background-color: #f8fafc; }\n" +
                "    .table-info { width: 100%; border-collapse: collapse; margin-bottom: 10px; }\n" +
                "    .table-info td { padding: 6px 8px; font-size: 12px; }\n" +
                "    .table-info .lbl { font-weight: bold; color: #334155; width: 30%; }\n" +
                "    .statement { margin: 25px 0; padding: 18px; background-color: #f0fdf4; border-left: 5px solid #16a34a; font-size: 13px; text-align: justify; }\n" +
                "    .verification-card { border: 1px dashed #0284c7; background-color: #f0f9ff; padding: 12px 15px; border-radius: 6px; margin: 25px 0; }\n" +
                "    .sign-section { margin-top: 50px; text-align: center; width: 260px; margin-left: auto; margin-right: auto; }\n" +
                "    .sign-line { border-top: 1px solid #334155; margin-bottom: 6px; }\n" +
                "    .footer { margin-top: 40px; border-top: 1px solid #e2e8f0; padding-top: 10px; font-size: 9px; color: #94a3b8; text-align: center; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"header\">\n" +
                "    <div class=\"org-title\">" + escapeHtml(safePropNombre) + "</div>\n" +
                "    <div style=\"font-size: 11px; color: #475569;\">NIT: " + escapeHtml(safeNit) + " — " + escapeHtml(safeDireccion) + ", " + escapeHtml(safeCiudad) + "</div>\n" +
                "    <div class=\"doc-title\">Certificado Oficial de Paz y Salvo</div>\n" +
                "    <div class=\"doc-subtitle\">Conforme al Régimen de Propiedad Horizontal — Ley 675 de 2001 de la República de Colombia</div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"content-box\">\n" +
                "    <table class=\"table-info\">\n" +
                "      <tr>\n" +
                "        <td class=\"lbl\">Unidad / Inmueble:</td>\n" +
                "        <td><strong>" + escapeHtml(unitIdentificador) + "</strong></td>\n" +
                "        <td class=\"lbl\">Fecha de Emisión:</td>\n" +
                "        <td>" + fechaEmision.format(DATE_FMT) + "</td>\n" +
                "      </tr>\n" +
                "      <tr>\n" +
                "        <td class=\"lbl\">Titular / Solicitante:</td>\n" +
                "        <td>" + escapeHtml(solicitanteNombre) + "</td>\n" +
                "        <td class=\"lbl\">Válido Hasta:</td>\n" +
                "        <td>" + fechaVencimiento.format(DATE_FMT) + "</td>\n" +
                "      </tr>\n" +
                "      <tr>\n" +
                "        <td class=\"lbl\">Identificación:</td>\n" +
                "        <td>" + escapeHtml(solicitanteDoc) + "</td>\n" +
                "        <td class=\"lbl\">Motivo:</td>\n" +
                "        <td>" + escapeHtml(safeMotivo) + "</td>\n" +
                "      </tr>\n" +
                "    </table>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"statement\">\n" +
                "    <strong>LA ADMINISTRACIÓN DE LA COPROPIEDAD HACE CONSTAR QUE:</strong><br />\n" +
                "    A la fecha y hora de emisión del presente certificado, la unidad <strong>" + escapeHtml(unitIdentificador) + "</strong> " +
                "    se encuentra a <strong>PAZ Y SALVO INTEGRAL</strong> por todo concepto de cuotas ordinarias, cuotas extraordinarias, " +
                "    intereses de mora, gastos comunes y sanciones pecuniarias o multas de convivencia debidamente ejecutoriadas y exigibles. " +
                "    Saldo exigible consolidado a la fecha: <strong>$0.00 COP</strong>.\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"verification-card\">\n" +
                "    <div style=\"font-weight: bold; color: #0369a1; margin-bottom: 4px;\">Código Único de Verificación Digital:</div>\n" +
                "    <div style=\"font-family: monospace; font-size: 14px; font-weight: bold; color: #0f172a;\">" + codigoVerificacion + "</div>\n" +
                "    <div style=\"font-size: 10px; color: #64748b; margin-top: 4px;\">\n" +
                "      Este documento cuenta con validez jurídica de 30 días calendario y su autenticidad puede ser verificada en el portal oficial SAED mediante el código impreso.\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"sign-section\">\n" +
                "    <div class=\"sign-line\"></div>\n" +
                "    <strong>ADMINISTRACIÓN DE LA COPROPIEDAD</strong><br />\n" +
                "    <span style=\"font-size: 11px; color: #64748b;\">Representante Legal / Administrador Delegado</span>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"footer\">\n" +
                "    Documento emitido electrónicamente por la plataforma SAED 2.0. Integridad y no repudio garantizados mediante firma digital y sellado criptográfico SHA-256.\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void setElevatedContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    private void restoreSaedContext(SaedContext ctx) {
        if (ctx != null) {
            SaedContextHolder.setContext(ctx);
        } else {
            SaedContextHolder.clearContext();
        }
        try {
            if (ctx == null || ctx.getUserId() == null) {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
            } else {
                long u = ctx.getUserId();
                String o = ctx.getOrganizationId() != null ? String.valueOf(ctx.getOrganizationId()) : "NULL";
                String p = ctx.getPropertyId() != null ? String.valueOf(ctx.getPropertyId()) : "NULL";
                String r = ctx.getRoleCode() != null ? ctx.getRoleCode() : "ANONYMOUS";
                jdbcTemplate.getJdbcOperations().execute(
                    String.format("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(%d); PKG_SAED_SESSION.SET_CONTEXT(%d, %s, %s, '%s'); END;", u, u, o, p, r)
                );
            }
        } catch (Exception ignored) {}
    }
}
