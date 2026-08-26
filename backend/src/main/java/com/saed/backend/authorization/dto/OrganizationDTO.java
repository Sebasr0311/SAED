package com.saed.backend.authorization.dto;

public class OrganizationDTO {
    private Long id;
    private String nombre;

    public OrganizationDTO() {}

    public OrganizationDTO(Long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
