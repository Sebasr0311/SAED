package com.saed.backend.person.repository;

import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import java.util.List;
import java.util.Optional;

public interface PersonaRepository {
    List<PersonaDTO> findAll(int limit, int offset);
    Optional<PersonaDTO> findById(Long id);
    Optional<PersonaDTO> findByUserId(Long userId);
    Optional<PersonaDTO> findByNumeroDocumento(String numeroDocumento);
    Optional<Long> findTipoDocumentoIdByCodigo(String codigo);
    Optional<Long> findUnidadIdByNumero(Long propiedadId, String identificador);
    void asignarUnidad(Long personaId, Long unidadId, String tipoRelacion);
    Long insert(PersonaRequestDTO request);
    void update(Long id, PersonaRequestDTO request);

    /** PERSONAS.ID_ORGANIZACION (home organization of the identity), if any. */
    Optional<Long> findOrganizationId(Long personaId);

    Optional<Long> findPersonaIdByUserId(Long userId);

    /** TIPO_RESIDENTE of an ACTIVE residence of the person in the unit, if any. */
    Optional<String> findActiveResidentType(Long personaId, Long unitId);

    /** Whether the person has an active residence or ownership in the unit. */
    boolean isLinkedToUnit(Long personaId, Long unitId);

    /**
     * F05-01: unlinks the person from the caller's scope only (a property, or every property of an organization):
     * residences, ownerships and unit/property assignments inside that scope are deactivated; links elsewhere are
     * preserved. The identity row is deactivated only when it belongs to the organization and no active link
     * remains. Returns the number of links deactivated.
     */
    int unlinkFromScope(Long personaId, Long propertyId, Long organizationId);
}
