package com.saed.backend.emergencias.service;

import com.saed.backend.emergencias.dto.*;

import java.util.List;

public interface EmergenciasService {
    List<PlanEmergenciaDTO> getAllPlanes();
    PlanEmergenciaDTO getPlanById(Long id);
    Long createPlan(PlanEmergenciaRequestDTO dto);
    void updatePlan(Long id, PlanEmergenciaRequestDTO dto);
    void deletePlan(Long id);

    List<ContactoEmergenciaDTO> getAllContactos();
    List<ContactoEmergenciaDTO> getContactosMinuta();
    ContactoEmergenciaDTO getContactoById(Long id);
    Long createContacto(ContactoEmergenciaRequestDTO dto);
    void updateContacto(Long id, ContactoEmergenciaRequestDTO dto);
    void deleteContacto(Long id);

    EmergenciasSummaryDTO getResumen();
}
