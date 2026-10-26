package com.saed.backend.person.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * DEC-01: PERSONAS has no row-level policy and is scoped in the application by PERSONAS.ID_ORGANIZACION, so a person
 * is always created in the organization of the server-side context, never from a request value and never as NULL.
 */
public final class PersonaOrganizationBinding {

    private PersonaOrganizationBinding() {
    }

    /**
     * Organization of the current tenant context: the context organization, or the one that owns the context
     * property. Fails closed when neither is known. Call it before any temporary context elevation.
     */
    public static Long contextOrganization(NamedParameterJdbcTemplate jdbc) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        if (ctx.getOrganizationId() != null) {
            return ctx.getOrganizationId();
        }
        if (ctx.getPropertyId() != null) {
            List<Long> orgs = jdbc.query("SELECT ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = :prop",
                    Map.of("prop", ctx.getPropertyId()), (rs, i) -> rs.getLong(1));
            if (orgs.size() == 1 && orgs.get(0) != null) {
                return orgs.get(0);
            }
        }
        throw new IllegalStateException("The tenant context has no organization");
    }

    /** Inserts a natural, active person in the given organization and returns the generated-key holder. */
    public static KeyHolder insertNaturalPerson(NamedParameterJdbcTemplate jdbc, Long organizationId, Long tipoDocumento,
            String numeroDocumento, String primerNombre, String primerApellido, String email, String telefono) {
        if (organizationId == null) {
            throw new IllegalArgumentException("A person must belong to an organization");
        }
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO,
                                      EMAIL, TELEFONO, ESTADO, ID_ORGANIZACION)
                VALUES (:tipoDoc, :doc, 'NATURAL', :nom, :ape, :email, :tel, 'ACTIVO', :org)
                """, new MapSqlParameterSource()
                        .addValue("tipoDoc", tipoDocumento)
                        .addValue("doc", numeroDocumento)
                        .addValue("nom", primerNombre)
                        .addValue("ape", primerApellido)
                        .addValue("email", email)
                        .addValue("tel", telefono)
                        .addValue("org", organizationId),
                keys, new String[] {"ID_PERSONA"});
        return keys;
    }
}
