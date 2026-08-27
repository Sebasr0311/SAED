package com.saed.backend.person.controller;

import com.saed.backend.person.dto.PersonaDTO;
import com.saed.backend.person.dto.PersonaRequestDTO;
import com.saed.backend.person.service.PersonaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Persona", description = "API para la gestion de Persona")
@RestController
@RequestMapping("/api/v1/personas")
public class PersonaController {
    
    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @GetMapping
    public ResponseEntity<List<PersonaDTO>> getPersonas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(personaService.getPersonas(page, size));
    }

    @PostMapping
    public ResponseEntity<Long> createPersona(@Valid @RequestBody PersonaRequestDTO request) {
        Long personaId = personaService.createPersona(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(personaId);
    }
}

