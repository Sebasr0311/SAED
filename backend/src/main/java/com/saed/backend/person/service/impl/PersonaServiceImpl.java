package com.saed.backend.person.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

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

    @Override
    @Transactional
    public com.saed.backend.person.dto.PersonaBatchResultDTO importarBatch(List<com.saed.backend.person.dto.PersonaBatchItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return new com.saed.backend.person.dto.PersonaBatchResultDTO(0, 0, 0, List.of());
        }

        Long propId = com.saed.backend.context.SaedContextHolder.getContext() != null
                ? com.saed.backend.context.SaedContextHolder.getContext().getPropertyId()
                : null;

        int procesados = 0;
        int fallidos = 0;
        List<com.saed.backend.person.dto.PersonaBatchFilaErrorDTO> errores = new java.util.ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            com.saed.backend.person.dto.PersonaBatchItemDTO item = items.get(i);
            int fila = i + 1;

            if (item.numeroDocumento() == null || item.numeroDocumento().isBlank()) {
                fallidos++;
                errores.add(new com.saed.backend.person.dto.PersonaBatchFilaErrorDTO(fila, "—", "El número de documento es obligatorio"));
                continue;
            }

            if (item.nombres() == null || item.nombres().isBlank() || item.apellidos() == null || item.apellidos().isBlank()) {
                fallidos++;
                errores.add(new com.saed.backend.person.dto.PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(), "Nombres y apellidos son obligatorios"));
                continue;
            }

            try {
                // 1. Resolver tipo de documento
                Long idTipoDoc = personaRepository.findTipoDocumentoIdByCodigo(item.tipoDocumento()).orElse(1L);

                // 2. Separar nombres y apellidos
                String[] nomParts = item.nombres().trim().split("\\s+", 2);
                String primerNombre = nomParts[0];
                String segundoNombre = nomParts.length > 1 ? nomParts[1] : "";

                String[] apeParts = item.apellidos().trim().split("\\s+", 2);
                String primerApellido = apeParts[0];
                String segundoApellido = apeParts.length > 1 ? apeParts[1] : "";

                PersonaRequestDTO req = new PersonaRequestDTO(
                        idTipoDoc,
                        item.numeroDocumento().trim(),
                        "NATURAL",
                        primerNombre,
                        segundoNombre,
                        primerApellido,
                        segundoApellido,
                        item.email() != null ? item.email().trim() : null,
                        item.telefono() != null ? item.telefono().trim() : null
                );

                // 3. Insertar o actualizar persona
                Long personaId;
                java.util.Optional<PersonaDTO> exist = personaRepository.findByNumeroDocumento(item.numeroDocumento().trim());
                if (exist.isPresent()) {
                    personaId = exist.get().id();
                    personaRepository.update(personaId, req);
                } else {
                    personaId = personaRepository.insert(req);
                }

                // 4. Asignar unidad si fue especificada
                if (item.apartamento() != null && !item.apartamento().isBlank() && propId != null) {
                    java.util.Optional<Long> unidadOpt = personaRepository.findUnidadIdByNumero(propId, item.apartamento().trim());
                    if (unidadOpt.isPresent()) {
                        personaRepository.asignarUnidad(personaId, unidadOpt.get(), item.tipoRelacion());
                    } else {
                        errores.add(new com.saed.backend.person.dto.PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(), 
                                "Persona guardada, pero la unidad/apartamento '" + item.apartamento().trim() + "' no existe en la propiedad activa"));
                    }
                }

                procesados++;
            } catch (Exception ex) {
                fallidos++;
                errores.add(new com.saed.backend.person.dto.PersonaBatchFilaErrorDTO(fila, item.numeroDocumento(), ex.getMessage()));
            }
        }

        return new com.saed.backend.person.dto.PersonaBatchResultDTO(items.size(), procesados, fallidos, errores);
    }
}
