package com.saed.backend.person.repository.impl;

import com.saed.backend.person.dto.*;
import com.saed.backend.person.repository.DependentRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Repository
public class DependentRepositoryImpl implements DependentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DependentRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- Mascotas ---
    @Override
    public MascotaDTO createMascota(MascotaRequestDTO request) {
        String sql = "INSERT INTO MASCOTAS (id_unidad, id_persona_responsable, nombre, especie, raza, color, genero, fecha_nacimiento_aprox, peso_kg, numero_microchip, es_raza_manejo_especial, poliza_responsabilidad_url, carnet_vacunacion_url, foto_url, estado) " +
                     "VALUES (:unidadId, :responsableId, :nombre, :especie, :raza, :color, :genero, :fechaNacimientoAprox, :pesoKg, :numeroMicrochip, :esRaza, :poliza, :carnet, :foto, :estado)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unidadId", request.unidadId())
                .addValue("responsableId", request.responsableId())
                .addValue("nombre", request.nombre())
                .addValue("especie", request.especie())
                .addValue("raza", request.raza())
                .addValue("color", request.color())
                .addValue("genero", request.genero() != null ? request.genero() : "M")
                .addValue("fechaNacimientoAprox", request.fechaNacimientoAprox())
                .addValue("pesoKg", request.pesoKg())
                .addValue("numeroMicrochip", request.numeroMicrochip())
                .addValue("esRaza", request.esRazaManejoEspecial() != null ? request.esRazaManejoEspecial() : "N")
                .addValue("poliza", request.polizaResponsabilidadUrl())
                .addValue("carnet", request.carnetVacunacionUrl())
                .addValue("foto", request.fotoUrl())
                .addValue("estado", request.estado() != null ? request.estado() : "ACTIVO");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_MASCOTA"});
        return getMascotaById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<MascotaDTO> getMascotaById(Long id) {
        String sql = "SELECT * FROM MASCOTAS WHERE id_mascota = :id";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> new MascotaDTO(
                rs.getLong("id_mascota"),
                rs.getLong("id_unidad"),
                rs.getLong("id_persona_responsable"),
                rs.getString("nombre"),
                rs.getString("especie"),
                rs.getString("raza"),
                rs.getString("color"),
                rs.getString("genero"),
                rs.getDate("fecha_nacimiento_aprox") != null ? rs.getDate("fecha_nacimiento_aprox").toLocalDate() : null,
                rs.getObject("peso_kg") != null ? rs.getDouble("peso_kg") : null,
                rs.getString("numero_microchip"),
                rs.getString("es_raza_manejo_especial"),
                rs.getString("poliza_responsabilidad_url"),
                rs.getString("carnet_vacunacion_url"),
                rs.getString("foto_url"),
                rs.getString("estado")
        )).stream().findFirst();
    }

    @Override
    public List<MascotaDTO> getMascotasByUnidad(Long unidadId) {
        String sql = "SELECT * FROM MASCOTAS WHERE id_unidad = :unidadId";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("unidadId", unidadId), (rs, rowNum) -> new MascotaDTO(
                rs.getLong("id_mascota"),
                rs.getLong("id_unidad"),
                rs.getLong("id_persona_responsable"),
                rs.getString("nombre"),
                rs.getString("especie"),
                rs.getString("raza"),
                rs.getString("color"),
                rs.getString("genero"),
                rs.getDate("fecha_nacimiento_aprox") != null ? rs.getDate("fecha_nacimiento_aprox").toLocalDate() : null,
                rs.getObject("peso_kg") != null ? rs.getDouble("peso_kg") : null,
                rs.getString("numero_microchip"),
                rs.getString("es_raza_manejo_especial"),
                rs.getString("poliza_responsabilidad_url"),
                rs.getString("carnet_vacunacion_url"),
                rs.getString("foto_url"),
                rs.getString("estado")
        ));
    }

    @Override
    public MascotaDTO updateMascota(Long id, MascotaRequestDTO request) {
        String sql = "UPDATE MASCOTAS SET nombre = :nombre, especie = :especie, raza = :raza, color = :color, genero = :genero, fecha_nacimiento_aprox = :fechaNacimientoAprox, peso_kg = :pesoKg, numero_microchip = :numeroMicrochip, es_raza_manejo_especial = :esRaza, poliza_responsabilidad_url = :poliza, carnet_vacunacion_url = :carnet, foto_url = :foto, estado = :estado WHERE id_mascota = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", request.nombre())
                .addValue("especie", request.especie())
                .addValue("raza", request.raza())
                .addValue("color", request.color())
                .addValue("genero", request.genero() != null ? request.genero() : "M")
                .addValue("fechaNacimientoAprox", request.fechaNacimientoAprox())
                .addValue("pesoKg", request.pesoKg())
                .addValue("numeroMicrochip", request.numeroMicrochip())
                .addValue("esRaza", request.esRazaManejoEspecial() != null ? request.esRazaManejoEspecial() : "N")
                .addValue("poliza", request.polizaResponsabilidadUrl())
                .addValue("carnet", request.carnetVacunacionUrl())
                .addValue("foto", request.fotoUrl())
                .addValue("estado", request.estado() != null ? request.estado() : "ACTIVO");
        jdbcTemplate.update(sql, params);
        return getMascotaById(id).orElseThrow();
    }

    @Override
    public void deleteMascota(Long id) {
        jdbcTemplate.update("DELETE FROM MASCOTAS WHERE id_mascota = :id", new MapSqlParameterSource("id", id));
    }

    // --- Vehiculos ---
    @Override
    public VehiculoDTO createVehiculo(VehiculoRequestDTO request) {
        String sql = "INSERT INTO VEHICULOS (id_persona, id_unidad, placa, tipo_vehiculo, marca, modelo, color, tag_rfid, estado) " +
                     "VALUES (:personaId, :unidadId, :placa, :tipoVehiculo, :marca, :modelo, :color, :tagRfid, :estado)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("personaId", request.personaId())
                .addValue("unidadId", request.unidadId())
                .addValue("placa", request.placa())
                .addValue("tipoVehiculo", request.tipoVehiculo())
                .addValue("marca", request.marca())
                .addValue("modelo", request.modelo())
                .addValue("color", request.color())
                .addValue("tagRfid", request.tagRfid())
                .addValue("estado", request.estado() != null ? request.estado() : "ACTIVO");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_VEHICULO"});
        return getVehiculoById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<VehiculoDTO> getVehiculoById(Long id) {
        String sql = "SELECT * FROM VEHICULOS WHERE id_vehiculo = :id";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> new VehiculoDTO(
                rs.getLong("id_vehiculo"),
                rs.getLong("id_persona"),
                rs.getObject("id_unidad") != null ? rs.getLong("id_unidad") : null,
                rs.getString("placa"),
                rs.getString("tipo_vehiculo"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("color"),
                rs.getString("tag_rfid"),
                rs.getString("estado"),
                rs.getTimestamp("fecha_creacion") != null ? ZonedDateTime.ofInstant(rs.getTimestamp("fecha_creacion").toInstant(), ZoneId.of("America/Bogota")) : null
        )).stream().findFirst();
    }

    @Override
    public List<VehiculoDTO> getVehiculosByUnidad(Long unidadId) {
        String sql = "SELECT * FROM VEHICULOS WHERE id_unidad = :unidadId";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("unidadId", unidadId), (rs, rowNum) -> new VehiculoDTO(
                rs.getLong("id_vehiculo"),
                rs.getLong("id_persona"),
                rs.getObject("id_unidad") != null ? rs.getLong("id_unidad") : null,
                rs.getString("placa"),
                rs.getString("tipo_vehiculo"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("color"),
                rs.getString("tag_rfid"),
                rs.getString("estado"),
                rs.getTimestamp("fecha_creacion") != null ? ZonedDateTime.ofInstant(rs.getTimestamp("fecha_creacion").toInstant(), ZoneId.of("America/Bogota")) : null
        ));
    }

    @Override
    public VehiculoDTO updateVehiculo(Long id, VehiculoRequestDTO request) {
        String sql = "UPDATE VEHICULOS SET placa = :placa, tipo_vehiculo = :tipoVehiculo, marca = :marca, modelo = :modelo, color = :color, tag_rfid = :tagRfid, estado = :estado WHERE id_vehiculo = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("placa", request.placa())
                .addValue("tipoVehiculo", request.tipoVehiculo())
                .addValue("marca", request.marca())
                .addValue("modelo", request.modelo())
                .addValue("color", request.color())
                .addValue("tagRfid", request.tagRfid())
                .addValue("estado", request.estado() != null ? request.estado() : "ACTIVO");
        jdbcTemplate.update(sql, params);
        return getVehiculoById(id).orElseThrow();
    }

    @Override
    public void deleteVehiculo(Long id) {
        jdbcTemplate.update("DELETE FROM VEHICULOS WHERE id_vehiculo = :id", new MapSqlParameterSource("id", id));
    }

    // --- Tutores ---
    @Override
    public TutorDTO createTutor(TutorRequestDTO request) {
        String sql = "INSERT INTO TUTORES (id_persona_menor, id_persona_tutor, parentesco, documento_soporte_url, estado) " +
                     "VALUES (:menorId, :tutorId, :parentesco, :documento, :estado)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("menorId", request.personaMenorId())
                .addValue("tutorId", request.personaTutorId())
                .addValue("parentesco", request.parentesco())
                .addValue("documento", request.documentoSoporteUrl())
                .addValue("estado", request.estado() != null ? request.estado() : "ACTIVO");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_TUTOR"});
        return getTutorById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<TutorDTO> getTutorById(Long id) {
        String sql = "SELECT * FROM TUTORES WHERE id_tutor = :id";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> new TutorDTO(
                rs.getLong("id_tutor"),
                rs.getLong("id_persona_menor"),
                rs.getLong("id_persona_tutor"),
                rs.getString("parentesco"),
                rs.getString("documento_soporte_url"),
                rs.getString("estado"),
                rs.getTimestamp("fecha_creacion") != null ? ZonedDateTime.ofInstant(rs.getTimestamp("fecha_creacion").toInstant(), ZoneId.of("America/Bogota")) : null
        )).stream().findFirst();
    }

    @Override
    public List<TutorDTO> getTutoresByMenor(Long menorId) {
        String sql = "SELECT * FROM TUTORES WHERE id_persona_menor = :menorId";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("menorId", menorId), (rs, rowNum) -> new TutorDTO(
                rs.getLong("id_tutor"),
                rs.getLong("id_persona_menor"),
                rs.getLong("id_persona_tutor"),
                rs.getString("parentesco"),
                rs.getString("documento_soporte_url"),
                rs.getString("estado"),
                rs.getTimestamp("fecha_creacion") != null ? ZonedDateTime.ofInstant(rs.getTimestamp("fecha_creacion").toInstant(), ZoneId.of("America/Bogota")) : null
        ));
    }

    @Override
    public TutorDTO updateTutor(Long id, TutorRequestDTO request) {
        String sql = "UPDATE TUTORES SET parentesco = :parentesco, documento_soporte_url = :documento, estado = :estado WHERE id_tutor = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("parentesco", request.parentesco())
                .addValue("documento", request.documentoSoporteUrl())
                .addValue("estado", request.estado() != null ? request.estado() : "ACTIVO");
        jdbcTemplate.update(sql, params);
        return getTutorById(id).orElseThrow();
    }

    @Override
    public void deleteTutor(Long id) {
        jdbcTemplate.update("DELETE FROM TUTORES WHERE id_tutor = :id", new MapSqlParameterSource("id", id));
    }

    // --- Visitantes ---
    @Override
    public VisitanteDTO createVisitante(VisitanteRequestDTO request) {
        String sql = "INSERT INTO VISITANTES (id_persona, es_frecuente, empresa, foto_url, observaciones, estado) " +
                     "VALUES (:personaId, :esFrecuente, :empresa, :foto, :obs, :estado)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("personaId", request.personaId())
                .addValue("esFrecuente", request.esFrecuente() != null ? request.esFrecuente() : "N")
                .addValue("empresa", request.empresa())
                .addValue("foto", request.fotoUrl())
                .addValue("obs", request.observaciones())
                .addValue("estado", request.estado() != null ? request.estado() : "ACTIVO");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_VISITANTE"});
        return getVisitanteById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<VisitanteDTO> getVisitanteById(Long id) {
        String sql = "SELECT * FROM VISITANTES WHERE id_visitante = :id";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> new VisitanteDTO(
                rs.getLong("id_visitante"),
                rs.getLong("id_persona"),
                rs.getString("es_frecuente"),
                rs.getString("empresa"),
                rs.getString("foto_url"),
                rs.getString("observaciones"),
                rs.getString("estado")
        )).stream().findFirst();
    }

    @Override
    public Optional<VisitanteDTO> getVisitanteByPersona(Long personaId) {
        String sql = "SELECT * FROM VISITANTES WHERE id_persona = :personaId";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("personaId", personaId), (rs, rowNum) -> new VisitanteDTO(
                rs.getLong("id_visitante"),
                rs.getLong("id_persona"),
                rs.getString("es_frecuente"),
                rs.getString("empresa"),
                rs.getString("foto_url"),
                rs.getString("observaciones"),
                rs.getString("estado")
        )).stream().findFirst();
    }

    @Override
    public VisitanteDTO updateVisitante(Long id, VisitanteRequestDTO request) {
        String sql = "UPDATE VISITANTES SET es_frecuente = :esFrecuente, empresa = :empresa, foto_url = :foto, observaciones = :obs, estado = :estado WHERE id_visitante = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("esFrecuente", request.esFrecuente() != null ? request.esFrecuente() : "N")
                .addValue("empresa", request.empresa())
                .addValue("foto", request.fotoUrl())
                .addValue("obs", request.observaciones())
                .addValue("estado", request.estado() != null ? request.estado() : "ACTIVO");
        jdbcTemplate.update(sql, params);
        return getVisitanteById(id).orElseThrow();
    }

    @Override
    public void deleteVisitante(Long id) {
        jdbcTemplate.update("DELETE FROM VISITANTES WHERE id_visitante = :id", new MapSqlParameterSource("id", id));
    }
}
