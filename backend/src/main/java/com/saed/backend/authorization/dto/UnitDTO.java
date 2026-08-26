package com.saed.backend.authorization.dto;

public class UnitDTO {
    private Long id;
    private String identificador;

    public UnitDTO() {}

    public UnitDTO(Long id, String identificador) {
        this.id = id;
        this.identificador = identificador;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }
}
