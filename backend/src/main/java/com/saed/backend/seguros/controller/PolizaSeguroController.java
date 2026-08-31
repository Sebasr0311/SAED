package com.saed.backend.seguros.controller;

import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.service.PolizaSeguroService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seguros/polizas")
@PreAuthorize("isAuthenticated()")
public class PolizaSeguroController {

    private final PolizaSeguroService service;

    public PolizaSeguroController(PolizaSeguroService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PolizaSeguroDTO>> getAll() {
        return ResponseEntity.ok(service.getAllPolizas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolizaSeguroDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPolizaById(id));
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody PolizaSeguroDTO dto) {
        service.createPoliza(dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody PolizaSeguroDTO dto) {
        service.updatePoliza(id, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deletePoliza(id);
        return ResponseEntity.ok().build();
    }
}
