package com.saed.backend.platform.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DEC-09: creates the default residential lease template of a newly provisioned organization.
 * Runs inside the provisioning transaction; nothing is seeded at application startup. The content
 * is the template previously seeded at startup by PlantillaContratoRepositoryImpl (removed in
 * 70f8c3f). Idempotent per organization.
 */
@Component
public class DefaultContractTemplateProvisioner {

    static final String CODE = "CONTRATO_ESTANDAR_2026";

    static final String DEFAULT_HTML = """
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

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DefaultContractTemplateProvisioner(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts the default template unless the organization already has one with the default code. */
    public void createDefaultTemplate(Long organizationId, Long createdBy) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId is required");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("org", organizationId)
                .addValue("codigo", CODE)
                .addValue("html", DEFAULT_HTML)
                .addValue("creadoPor", createdBy);
        jdbcTemplate.update("""
                INSERT INTO PLANTILLAS_CONTRATOS (
                    ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, DESCRIPCION,
                    CONTENIDO_HTML, VARIABLES_DISPONIBLES, CAMPOS_REQUERIDOS, VERSION,
                    ESTADO, VIGENCIA_DESDE, CREADO_POR
                )
                SELECT :org, :codigo, 'Contrato Estándar Residencial', 'INICIAL',
                       'Plantilla base predeterminada para contratos de arrendamiento residencial.',
                       :html,
                       '["propiedad.nombre","inquilino.nombre_completo","apartamento.numero","contrato.canon_mensual"]',
                       '["propiedad.nombre","inquilino.nombre_completo","apartamento.numero"]', 1,
                       'ACTIVA', TRUNC(SYSDATE), :creadoPor
                FROM DUAL
                WHERE NOT EXISTS (
                    SELECT 1 FROM PLANTILLAS_CONTRATOS WHERE ID_ORGANIZACION = :org AND CODIGO = :codigo
                )
                """, params);
    }
}
