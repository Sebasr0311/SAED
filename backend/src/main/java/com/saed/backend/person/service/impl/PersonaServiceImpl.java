package com.saed.backend.person.service.impl;

import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.repository.PersonaRepository;
import com.saed.backend.person.service.PersonaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PersonaServiceImpl implements PersonaService {
    
    private final PersonaRepository personaRepository;

    public PersonaServiceImpl(PersonaRepository personaRepository) {
        this.personaRepository = personaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonaDTO> getPersonas(int page, int size) {
        int offset = page * size;
        return personaRepository.findAll(size, offset);
    }

    @Override
    @Transactional
    public Long createPersona(PersonaRequestDTO request) {
        return personaRepository.insert(request);
    }

    @Override
    @Transactional
    public void updatePersona(Long id, PersonaRequestDTO request) {
        personaRepository.update(id, request);
    }

    @Override
    @Transactional
    public void deletePersona(Long id) {
        personaRepository.delete(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonaDTO getPersonaById(Long id) {
        return personaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Persona no encontrada con ID: " + id));
    }
}
