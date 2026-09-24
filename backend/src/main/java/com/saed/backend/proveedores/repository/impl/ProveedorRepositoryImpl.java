package com.saed.backend.proveedores.repository.impl;

import com.saed.backend.proveedores.dto.ProveedorCreateDTO;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.dto.ProveedorUpdateDTO;
import com.saed.backend.proveedores.repository.ProveedorRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
public class ProveedorRepositoryImpl implements ProveedorRepository {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final NamedParameterJdbcTemplate jdbc;

    public ProveedorRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<ProveedorDTO> rowMapper = new RowMapper<>() {
        @Override
        public ProveedorDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp ts = rs.getTimestamp("FECHA_CREACION");
            OffsetDateTime fechaCreacion = (ts != null) ? ts.toInstant().atZone(BOGOTA_ZONE).toOffsetDateTime() : null;

            return new ProveedorDTO(
                rs.getLong("ID_PROVEEDOR"),
                rs.getLong("ID_ORGANIZACION"),
                rs.getString("TIPO_PERSONA"),
                rs.getString("RAZON_SOCIAL"),
                rs.getString("NIT_IDENTIFICACION"),
                rs.getString("EMAIL_CONTACTO"),
                rs.getString("TELEFONO_CONTACTO"),
                rs.getString("DIRECCION"),
                rs.getString("CIUDAD"),
                rs.getString("CATEGORIA_SERVICIO"),
                rs.getBigDecimal("CALIFICACION_PROM"),
                rs.getString("ESTADO"),
                fechaCreacion
            );
        }
    };

    @Override
    public ProveedorDTO crear(Long idOrganizacion, ProveedorCreateDTO dto) {
        String sql = """
            INSERT INTO PROVEEDORES (
                ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION,
                EMAIL_CONTACTO, TELEFONO_CONTACTO, DIRECCION, CIUDAD,
                CATEGORIA_SERVICIO, CALIFICACION_PROM, ESTADO
            ) VALUES (
                :idOrg, :tipoPersona, :razonSocial, :nit,
                :email, :telefono, :direccion, :ciudad,
                :categoria, :calificacion, 'ACTIVO'
            )
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idOrg", idOrganizacion)
                .addValue("tipoPersona", dto.tipoPersona() != null ? dto.tipoPersona().trim().toUpperCase() : "JURIDICA")
                .addValue("razonSocial", dto.razonSocial().trim())
                .addValue("nit", dto.nitIdentificacion().trim())
                .addValue("email", dto.emailContacto().trim())
                .addValue("telefono", dto.telefonoContacto() != null ? dto.telefonoContacto().trim() : null)
                .addValue("direccion", dto.direccion() != null ? dto.direccion().trim() : null)
                .addValue("ciudad", dto.ciudad() != null ? dto.ciudad().trim() : null)
                .addValue("categoria", dto.categoriaServicio().trim())
                .addValue("calificacion", dto.calificacionProm() != null ? dto.calificacionProm() : new BigDecimal("5.00"));

        jdbc.update(sql, params, keyHolder, new String[]{"ID_PROVEEDOR"});
        Number key = keyHolder.getKey();
        Long id = (key != null) ? key.longValue() : null;

        return buscarPorId(id, idOrganizacion).orElseThrow(() ->
                new IllegalStateException("No se pudo recuperar el proveedor recién creado con ID: " + id));
    }

    @Override
    public List<ProveedorDTO> listar(Long idOrganizacion, String estado, String categoria, String search) {
        StringBuilder sql = new StringBuilder("""
            SELECT ID_PROVEEDOR, ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION,
                   EMAIL_CONTACTO, TELEFONO_CONTACTO, DIRECCION, CIUDAD,
                   CATEGORIA_SERVICIO, CALIFICACION_PROM, ESTADO, FECHA_CREACION
            FROM PROVEEDORES
            WHERE ID_ORGANIZACION = :idOrg
            """);

        MapSqlParameterSource params = new MapSqlParameterSource("idOrg", idOrganizacion);

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND ESTADO = :estado");
            params.addValue("estado", estado.trim().toUpperCase());
        }

        if (categoria != null && !categoria.isBlank()) {
            sql.append(" AND UPPER(CATEGORIA_SERVICIO) = UPPER(:categoria)");
            params.addValue("categoria", categoria.trim());
        }

        if (search != null && !search.isBlank()) {
            sql.append(" AND (UPPER(RAZON_SOCIAL) LIKE :search OR UPPER(NIT_IDENTIFICACION) LIKE :search)");
            params.addValue("search", "%" + search.trim().toUpperCase() + "%");
        }

        sql.append(" ORDER BY RAZON_SOCIAL ASC, ID_PROVEEDOR DESC");

        return jdbc.query(sql.toString(), params, rowMapper);
    }

    @Override
    public Optional<ProveedorDTO> buscarPorId(Long idProveedor, Long idOrganizacion) {
        String sql = """
            SELECT ID_PROVEEDOR, ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION,
                   EMAIL_CONTACTO, TELEFONO_CONTACTO, DIRECCION, CIUDAD,
                   CATEGORIA_SERVICIO, CALIFICACION_PROM, ESTADO, FECHA_CREACION
            FROM PROVEEDORES
            WHERE ID_PROVEEDOR = :idProv AND ID_ORGANIZACION = :idOrg
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idProv", idProveedor)
                .addValue("idOrg", idOrganizacion);

        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ProveedorDTO> buscarPorIdDirecto(Long idProveedor) {
        String sql = """
            SELECT ID_PROVEEDOR, ID_ORGANIZACION, TIPO_PERSONA, RAZON_SOCIAL, NIT_IDENTIFICACION,
                   EMAIL_CONTACTO, TELEFONO_CONTACTO, DIRECCION, CIUDAD,
                   CATEGORIA_SERVICIO, CALIFICACION_PROM, ESTADO, FECHA_CREACION
            FROM PROVEEDORES
            WHERE ID_PROVEEDOR = :idProv
            """;

        MapSqlParameterSource params = new MapSqlParameterSource("idProv", idProveedor);

        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existePorNit(Long idOrganizacion, String nitIdentificacion, Long excluirIdProveedor) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) FROM PROVEEDORES
            WHERE ID_ORGANIZACION = :idOrg AND UPPER(NIT_IDENTIFICACION) = UPPER(:nit)
            """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idOrg", idOrganizacion)
                .addValue("nit", nitIdentificacion.trim());

        if (excluirIdProveedor != null) {
            sql.append(" AND ID_PROVEEDOR != :excluirId");
            params.addValue("excluirId", excluirIdProveedor);
        }

        Number count = jdbc.queryForObject(sql.toString(), params, Number.class);
        return count != null && count.intValue() > 0;
    }

    @Override
    public void actualizar(Long idProveedor, Long idOrganizacion, ProveedorUpdateDTO dto) {
        String sql = """
            UPDATE PROVEEDORES SET
                TIPO_PERSONA = :tipoPersona,
                RAZON_SOCIAL = :razonSocial,
                NIT_IDENTIFICACION = :nit,
                EMAIL_CONTACTO = :email,
                TELEFONO_CONTACTO = :telefono,
                DIRECCION = :direccion,
                CIUDAD = :ciudad,
                CATEGORIA_SERVICIO = :categoria,
                CALIFICACION_PROM = COALESCE(:calificacion, CALIFICACION_PROM)
            WHERE ID_PROVEEDOR = :idProv AND ID_ORGANIZACION = :idOrg
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idProv", idProveedor)
                .addValue("idOrg", idOrganizacion)
                .addValue("tipoPersona", dto.tipoPersona() != null ? dto.tipoPersona().trim().toUpperCase() : "JURIDICA")
                .addValue("razonSocial", dto.razonSocial().trim())
                .addValue("nit", dto.nitIdentificacion().trim())
                .addValue("email", dto.emailContacto().trim())
                .addValue("telefono", dto.telefonoContacto() != null ? dto.telefonoContacto().trim() : null)
                .addValue("direccion", dto.direccion() != null ? dto.direccion().trim() : null)
                .addValue("ciudad", dto.ciudad() != null ? dto.ciudad().trim() : null)
                .addValue("categoria", dto.categoriaServicio().trim())
                .addValue("calificacion", dto.calificacionProm());

        jdbc.update(sql, params);
    }

    @Override
    public void actualizarEstado(Long idProveedor, Long idOrganizacion, String nuevoEstado) {
        String sql = """
            UPDATE PROVEEDORES SET ESTADO = :estado
            WHERE ID_PROVEEDOR = :idProv AND ID_ORGANIZACION = :idOrg
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idProv", idProveedor)
                .addValue("idOrg", idOrganizacion)
                .addValue("estado", nuevoEstado.trim().toUpperCase());

        jdbc.update(sql, params);
    }
}
