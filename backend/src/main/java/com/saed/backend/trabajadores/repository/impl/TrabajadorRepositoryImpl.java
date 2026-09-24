package com.saed.backend.trabajadores.repository.impl;

import com.saed.backend.trabajadores.dto.TrabajadorCreateDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorUpdateDTO;
import com.saed.backend.trabajadores.repository.TrabajadorRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class TrabajadorRepositoryImpl implements TrabajadorRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public TrabajadorRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<TrabajadorDTO> rowMapper = (rs, rowNum) -> {
        Date sqlAfiliacion = rs.getDate("ARL_FECHA_AFILIACION");
        LocalDate arlFechaAfiliacion = (sqlAfiliacion != null) ? sqlAfiliacion.toLocalDate() : null;

        Date sqlVencimiento = rs.getDate("ARL_FECHA_VENCIMIENTO");
        LocalDate arlFechaVencimiento = (sqlVencimiento != null) ? sqlVencimiento.toLocalDate() : null;

        Date sqlAlturas = rs.getDate("CERT_ALTURAS_VENCIMIENTO");
        LocalDate certAlturasVencimiento = (sqlAlturas != null) ? sqlAlturas.toLocalDate() : null;

        String pNom = rs.getString("PRIMER_NOMBRE");
        String sNom = rs.getString("SEGUNDO_NOMBRE");
        String pApe = rs.getString("PRIMER_APELLIDO");
        String sApe = rs.getString("SEGUNDO_APELLIDO");

        StringBuilder sb = new StringBuilder();
        if (pNom != null && !pNom.isBlank()) sb.append(pNom.trim());
        if (sNom != null && !sNom.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(sNom.trim());
        }
        if (pApe != null && !pApe.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(pApe.trim());
        }
        if (sApe != null && !sApe.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(sApe.trim());
        }
        String nombreCompleto = sb.toString();

        boolean arlVigente = TrabajadorDTO.calcularArlVigente(arlFechaVencimiento, arlFechaAfiliacion);

        Long idProv = rs.getLong("ID_PROVEEDOR");
        if (rs.wasNull()) idProv = null;

        return new TrabajadorDTO(
                rs.getLong("ID_TRABAJADOR"),
                rs.getLong("ID_PERSONA"),
                idProv,
                rs.getString("RAZON_SOCIAL_PROVEEDOR"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                pNom,
                sNom,
                pApe,
                sApe,
                nombreCompleto,
                rs.getString("EMAIL"),
                rs.getString("TELEFONO"),
                rs.getString("OFICIO_ESPECIALIDAD"),
                rs.getString("ARL_ASEGURADORA"),
                arlFechaAfiliacion,
                arlFechaVencimiento,
                certAlturasVencimiento,
                rs.getString("EMPRESA_INDEPENDIENTE"),
                rs.getString("ESTADO"),
                arlVigente
        );
    };

    private static final String BASE_SELECT = """
            SELECT t.ID_TRABAJADOR,
                   t.ID_PERSONA,
                   t.ID_PROVEEDOR,
                   pr.RAZON_SOCIAL AS RAZON_SOCIAL_PROVEEDOR,
                   COALESCE(td.CODIGO, td.NOMBRE, 'CC') AS TIPO_DOCUMENTO,
                   p.NUMERO_DOCUMENTO,
                   p.PRIMER_NOMBRE,
                   p.SEGUNDO_NOMBRE,
                   p.PRIMER_APELLIDO,
                   p.SEGUNDO_APELLIDO,
                   p.EMAIL,
                   p.TELEFONO,
                   t.OFICIO_ESPECIALIDAD,
                   t.ARL_ASEGURADORA,
                   t.ARL_FECHA_AFILIACION,
                   t.ARL_FECHA_VENCIMIENTO,
                   t.CERT_ALTURAS_VENCIMIENTO,
                   t.EMPRESA_INDEPENDIENTE,
                   t.ESTADO
            FROM TRABAJADORES t
            JOIN PERSONAS p ON t.ID_PERSONA = p.ID_PERSONA
            LEFT JOIN PROVEEDORES pr ON t.ID_PROVEEDOR = pr.ID_PROVEEDOR
            LEFT JOIN TIPOS_DOCUMENTO td ON p.ID_TIPO_DOCUMENTO = td.ID_TIPO_DOCUMENTO
            """;

    @Override
    public TrabajadorDTO crear(Long idPersona, TrabajadorCreateDTO dto) {
        String sql = """
                INSERT INTO TRABAJADORES (
                    ID_PERSONA, ID_PROVEEDOR, EMPRESA_INDEPENDIENTE, OFICIO_ESPECIALIDAD,
                    ARL_ASEGURADORA, ARL_FECHA_AFILIACION, ARL_FECHA_VENCIMIENTO, CERT_ALTURAS_VENCIMIENTO, ESTADO
                ) VALUES (
                    :idPersona, :idProveedor, :empresaIndependiente, :oficioEspecialidad,
                    :arlAseguradora, :arlFechaAfiliacion, :arlFechaVencimiento, :certAlturasVencimiento, :estado
                )
                """;

        String estado = (dto.estado() != null && !dto.estado().isBlank()) ? dto.estado().trim().toUpperCase() : "ACTIVO";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPersona", idPersona, Types.NUMERIC)
                .addValue("idProveedor", dto.idProveedor(), Types.NUMERIC)
                .addValue("empresaIndependiente", dto.empresaIndependiente(), Types.VARCHAR)
                .addValue("oficioEspecialidad", dto.oficioEspecialidad(), Types.VARCHAR)
                .addValue("arlAseguradora", dto.arlAseguradora(), Types.VARCHAR)
                .addValue("arlFechaAfiliacion", dto.arlFechaAfiliacion() != null ? Date.valueOf(dto.arlFechaAfiliacion()) : null, Types.DATE)
                .addValue("arlFechaVencimiento", Date.valueOf(dto.arlFechaVencimiento()), Types.DATE)
                .addValue("certAlturasVencimiento", dto.certAlturasVencimiento() != null ? Date.valueOf(dto.certAlturasVencimiento()) : null, Types.DATE)
                .addValue("estado", estado, Types.VARCHAR);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"ID_TRABAJADOR"});

        Number key = keyHolder.getKey();
        Long idTrabajador = (key != null) ? key.longValue() : null;

        return buscarPorIdDirecto(idTrabajador).orElseThrow(() ->
                new IllegalStateException("No se pudo recuperar el trabajador recién creado con id " + idTrabajador));
    }

    @Override
    public List<TrabajadorDTO> listar(Long idOrganizacion, Long idProveedor, String estado, String search) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        sql.append(" WHERE 1=1");

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (idOrganizacion != null) {
            sql.append(" AND pr.ID_ORGANIZACION = :idOrganizacion");
            params.addValue("idOrganizacion", idOrganizacion);
        }

        if (idProveedor != null) {
            sql.append(" AND t.ID_PROVEEDOR = :idProveedor");
            params.addValue("idProveedor", idProveedor);
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND t.ESTADO = :estado");
            params.addValue("estado", estado.trim().toUpperCase());
        }

        if (search != null && !search.isBlank()) {
            sql.append("""
                     AND (LOWER(p.NUMERO_DOCUMENTO) LIKE :search
                          OR LOWER(p.PRIMER_NOMBRE) LIKE :search
                          OR LOWER(p.PRIMER_APELLIDO) LIKE :search
                          OR LOWER(t.OFICIO_ESPECIALIDAD) LIKE :search
                          OR LOWER(t.ARL_ASEGURADORA) LIKE :search
                          OR LOWER(pr.RAZON_SOCIAL) LIKE :search)
                    """);
            params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        }

        sql.append(" ORDER BY t.ID_TRABAJADOR DESC");

        return jdbc.query(sql.toString(), params, rowMapper);
    }

    @Override
    public Optional<TrabajadorDTO> buscarPorId(Long idTrabajador, Long idOrganizacion) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        sql.append(" WHERE t.ID_TRABAJADOR = :idTrabajador");

        MapSqlParameterSource params = new MapSqlParameterSource("idTrabajador", idTrabajador);

        if (idOrganizacion != null) {
            sql.append(" AND pr.ID_ORGANIZACION = :idOrganizacion");
            params.addValue("idOrganizacion", idOrganizacion);
        }

        try {
            return Optional.ofNullable(jdbc.queryForObject(sql.toString(), params, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TrabajadorDTO> buscarPorIdDirecto(Long idTrabajador) {
        String sql = BASE_SELECT + " WHERE t.ID_TRABAJADOR = :idTrabajador";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("idTrabajador", idTrabajador), rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TrabajadorDTO> buscarPorIdForUpdate(Long idTrabajador) {
        String sql = BASE_SELECT + " WHERE t.ID_TRABAJADOR = :idTrabajador FOR UPDATE";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, new MapSqlParameterSource("idTrabajador", idTrabajador), rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existePorProveedorYPersona(Long idProveedor, Long idPersona, Long excluirIdTrabajador) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idProveedor", idProveedor, Types.NUMERIC)
                .addValue("idPersona", idPersona, Types.NUMERIC);

        String sql;
        if (excluirIdTrabajador != null) {
            sql = """
                    SELECT COUNT(1)
                    FROM TRABAJADORES
                    WHERE ID_PROVEEDOR = :idProveedor
                      AND ID_PERSONA = :idPersona
                      AND ID_TRABAJADOR <> :excluir
                    """;
            params.addValue("excluir", excluirIdTrabajador, Types.NUMERIC);
        } else {
            sql = """
                    SELECT COUNT(1)
                    FROM TRABAJADORES
                    WHERE ID_PROVEEDOR = :idProveedor
                      AND ID_PERSONA = :idPersona
                    """;
        }

        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void actualizar(Long idTrabajador, Long idOrganizacion, TrabajadorUpdateDTO dto) {
        StringBuilder sql = new StringBuilder("""
                UPDATE TRABAJADORES SET
                    OFICIO_ESPECIALIDAD = COALESCE(:oficioEspecialidad, OFICIO_ESPECIALIDAD),
                    ARL_ASEGURADORA = COALESCE(:arlAseguradora, ARL_ASEGURADORA),
                    ARL_FECHA_AFILIACION = :arlFechaAfiliacion,
                    ARL_FECHA_VENCIMIENTO = COALESCE(:arlFechaVencimiento, ARL_FECHA_VENCIMIENTO),
                    CERT_ALTURAS_VENCIMIENTO = :certAlturasVencimiento,
                    EMPRESA_INDEPENDIENTE = COALESCE(:empresaIndependiente, EMPRESA_INDEPENDIENTE),
                    ESTADO = COALESCE(:estado, ESTADO)
                WHERE ID_TRABAJADOR = :idTrabajador
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idTrabajador", idTrabajador, Types.NUMERIC)
                .addValue("oficioEspecialidad", dto.oficioEspecialidad(), Types.VARCHAR)
                .addValue("arlAseguradora", dto.arlAseguradora(), Types.VARCHAR)
                .addValue("arlFechaAfiliacion", dto.arlFechaAfiliacion() != null ? Date.valueOf(dto.arlFechaAfiliacion()) : null, Types.DATE)
                .addValue("arlFechaVencimiento", dto.arlFechaVencimiento() != null ? Date.valueOf(dto.arlFechaVencimiento()) : null, Types.DATE)
                .addValue("certAlturasVencimiento", dto.certAlturasVencimiento() != null ? Date.valueOf(dto.certAlturasVencimiento()) : null, Types.DATE)
                .addValue("empresaIndependiente", dto.empresaIndependiente(), Types.VARCHAR)
                .addValue("estado", dto.estado(), Types.VARCHAR);

        if (idOrganizacion != null) {
            sql.append(" AND ID_PROVEEDOR IN (SELECT ID_PROVEEDOR FROM PROVEEDORES WHERE ID_ORGANIZACION = :idOrganizacion)");
            params.addValue("idOrganizacion", idOrganizacion, Types.NUMERIC);
        }

        jdbc.update(sql.toString(), params);
    }

    @Override
    public void actualizarEstado(Long idTrabajador, Long idOrganizacion, String nuevoEstado) {
        StringBuilder sql = new StringBuilder("""
                UPDATE TRABAJADORES SET
                    ESTADO = :nuevoEstado
                WHERE ID_TRABAJADOR = :idTrabajador
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idTrabajador", idTrabajador)
                .addValue("nuevoEstado", nuevoEstado);

        if (idOrganizacion != null) {
            sql.append(" AND ID_PROVEEDOR IN (SELECT ID_PROVEEDOR FROM PROVEEDORES WHERE ID_ORGANIZACION = :idOrganizacion)");
            params.addValue("idOrganizacion", idOrganizacion);
        }

        jdbc.update(sql.toString(), params);
    }
}
