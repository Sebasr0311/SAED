package com.saed.backend.person.repository;

import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import java.util.List;
import java.util.Optional;

public interface UnitInhabitantRepository {
    List<UnitOwnerDTO> findOwnersByUnitId(Long unitId);
    Long insertOwner(Long unitId, UnitOwnerRequestDTO request);
    
    List<UnitResidentDTO> findResidentsByUnitId(Long unitId);
    Optional<UnitResidentDTO> findResidentByIdAndUnitId(Long unitId, Long residentId);
    Long insertResident(Long unitId, UnitResidentRequestDTO request);

    int updateResidentStatus(Long unitId, Long residentId, String status);
    int unlinkResident(Long unitId, Long residentId);
    int deleteResidentPermanently(Long unitId, Long residentId);

    /** Property and organization of a unit (visible to the caller). */
    record UnitScope(Long propertyId, Long organizationId) {}

    Optional<UnitScope> findUnitScope(Long unitId);

    /**
     * Whether the person belongs to the organization: its home organization is that one, or (legacy identity without
     * a home organization) it is already linked to a unit of one of its properties. PERSONAS has no row-level policy
     * (DEC-01), so linking a person to a unit must check this explicitly.
     */
    boolean personBelongsToOrganization(Long personaId, Long organizationId);

    /**
     * Deactivates the resident-level assignments (RESIDENTE, RESIDENTE_CONVIVENCIA) of the person for this unit only,
     * in the caller's database context (administrators). Other units, properties and accounts are untouched.
     */
    int revokeUnitResidentAccess(Long unitId, Long personaId);

    /**
     * Same revocation for a titular removing a household member. Row-level policies only let a resident see their
     * own assignments, so this goes through the security-schema procedure SP_REVOCAR_ACCESO_UNIDAD, which validates
     * that the caller is the titular of the unit and that the member's residence there is no longer active.
     */
    void revokeHouseholdAccessAsTitular(Long unitId, Long personaId);
}
