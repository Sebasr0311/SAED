package com.saed.backend.catalog.controller;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * CatalogoController — catalogos maestros del modelo 2.0.
 * Expone los catalogos necesarios para formularios (unidades, propiedades,
 * bloques, personas) filtrados por el tenant activo via RLS.
 */
@Tag(name = "Catalogos", description = "Catalogos maestros SAED 2.0")
@RestController
@RequestMapping("/api/v1")
public class CatalogoController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CatalogoController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/tipos-unidad")
    public List<Map<String, Object>> tiposUnidad() {
        return jdbcTemplate.queryForList(
                "SELECT ID_TIPO_UNIDAD, CODIGO, NOMBRE FROM TIPOS_UNIDAD ORDER BY NOMBRE",
                new MapSqlParameterSource());
    }

    @GetMapping("/bloques")
    public List<Map<String, Object>> bloques() {
        return jdbcTemplate.queryForList(
                "SELECT ID_BLOQUE, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ORDEN, ESTADO FROM BLOQUES ORDER BY ORDEN, CODIGO",
                new MapSqlParameterSource());
    }

    @GetMapping("/tipos-propiedad")
    public List<Map<String, Object>> tiposPropiedad() {
        return jdbcTemplate.queryForList(
                "SELECT ID_TIPO_PROPIEDAD, CODIGO, NOMBRE FROM TIPOS_PROPIEDAD ORDER BY NOMBRE",
                new MapSqlParameterSource());
    }

    @GetMapping("/tipos-documento")
    public List<Map<String, Object>> tiposDocumento() {
        return jdbcTemplate.queryForList(
                "SELECT ID_TIPO_DOCUMENTO, CODIGO, NOMBRE, APLICA_PERSONA_NATURAL, APLICA_PERSONA_JURIDICA FROM TIPOS_DOCUMENTO ORDER BY NOMBRE",
                new MapSqlParameterSource());
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return jdbcTemplate.queryForList(
                "SELECT ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO FROM ROLES ORDER BY NOMBRE",
                new MapSqlParameterSource());
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios() {
        return jdbcTemplate.queryForList(
                "SELECT u.ID_USUARIO, u.NOMBRE_USUARIO, u.EMAIL, u.ESTADO " +
                "FROM USUARIOS u ORDER BY u.NOMBRE_USUARIO",
                new MapSqlParameterSource());
    }
}