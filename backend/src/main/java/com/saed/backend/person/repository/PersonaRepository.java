package com.saed.backend.person.repository;

import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import java.util.List;
import java.util.Optional;

public interface PersonaRepository {
    List<PersonaDTO> findAll(int limit, int offset);
    Optional<PersonaDTO> findById(Long id);
    Optional<PersonaDTO> findByNumeroDocumento(String numeroDocumento);
    Optional<Long> findTipoDocumentoIdByCodigo(String codigo);
    Optional<Long> findUnidadIdByNumero(Long propiedadId, String identificador);
    void asignarUnidad(Long personaId, Long unidadId, String tipoRelacion);
    Long insert(PersonaRequestDTO request);
    void update(Long id, PersonaRequestDTO request);
    void delete(Long id);
}
