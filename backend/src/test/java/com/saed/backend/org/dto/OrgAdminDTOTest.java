package com.saed.backend.org.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrgAdminDTOTest {

    @Test
    void testCreateOrgAdminRequestDTO_withMultipleProperties() {
        CreateOrgAdminRequestDTO dto = new CreateOrgAdminRequestDTO();
        dto.setIdPropiedades(List.of(101L, 102L, 103L));

        List<Long> resolved = dto.getResolvedPropiedades();
        assertEquals(3, resolved.size());
        assertTrue(resolved.contains(101L));
        assertTrue(resolved.contains(102L));
        assertTrue(resolved.contains(103L));
    }

    @Test
    void testCreateOrgAdminRequestDTO_withLegacySingleProperty() {
        CreateOrgAdminRequestDTO dto = new CreateOrgAdminRequestDTO();
        dto.setIdPropiedad(55L);

        List<Long> resolved = dto.getResolvedPropiedades();
        assertEquals(1, resolved.size());
        assertEquals(55L, resolved.get(0));
    }

    @Test
    void testCreateOrgAdminRequestDTO_withNoProperties() {
        CreateOrgAdminRequestDTO dto = new CreateOrgAdminRequestDTO();

        List<Long> resolved = dto.getResolvedPropiedades();
        assertNotNull(resolved);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void testUpdateOrgAdminRequestDTO_withMultipleProperties() {
        UpdateOrgAdminRequestDTO dto = new UpdateOrgAdminRequestDTO();
        dto.setIdPropiedades(List.of(201L, 202L));

        List<Long> resolved = dto.getResolvedPropiedades();
        assertEquals(2, resolved.size());
        assertEquals(201L, resolved.get(0));
        assertEquals(202L, resolved.get(1));
    }

    @Test
    void testUpdateOrgAdminRequestDTO_withLegacySingleProperty() {
        UpdateOrgAdminRequestDTO dto = new UpdateOrgAdminRequestDTO();
        dto.setIdPropiedad(77L);

        List<Long> resolved = dto.getResolvedPropiedades();
        assertEquals(1, resolved.size());
        assertEquals(77L, resolved.get(0));
    }

    @Test
    void testOrgAdminDTO_aggregationFields() {
        OrgAdminDTO dto = new OrgAdminDTO();
        dto.setIdUsuario(10L);
        dto.setIdPropiedades(List.of(1L, 2L));
        dto.setPropiedadesNombres(List.of("Edificio A", "Edificio B"));

        OrgAdminDTO.AdminPropertyAssignmentDTO asig1 =
                new OrgAdminDTO.AdminPropertyAssignmentDTO(1001L, 1L, "Edificio A", "ACTIVA");
        OrgAdminDTO.AdminPropertyAssignmentDTO asig2 =
                new OrgAdminDTO.AdminPropertyAssignmentDTO(1002L, 2L, "Edificio B", "ACTIVA");

        dto.setPropiedades(List.of(asig1, asig2));

        assertEquals(2, dto.getIdPropiedades().size());
        assertEquals(2, dto.getPropiedades().size());
        assertEquals("Edificio A", dto.getPropiedades().get(0).getPropiedadNombre());
        assertEquals("ACTIVA", dto.getPropiedades().get(0).getEstado());
    }
}
