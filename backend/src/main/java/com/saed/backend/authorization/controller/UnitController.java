package com.saed.backend.authorization.controller;

import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.authorization.service.UnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public ResponseEntity<List<UnitDTO>> findAll() {
        return ResponseEntity.ok(unitService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnitDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(unitService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody UnitRequestDTO request) {
        Long id = unitService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UnitRequestDTO request) {
        unitService.update(id, request);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
