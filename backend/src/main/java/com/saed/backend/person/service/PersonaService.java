package com.saed.backend.person.service;

import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import java.util.List;

public interface PersonaService {
    List<PersonaDTO> getPersonas(int page, int size);
    Long createPersona(PersonaRequestDTO request);
    void updatePersona(Long id, PersonaRequestDTO request);
    void deletePersona(Long id);
    PersonaDTO getPersonaById(Long id);
}
