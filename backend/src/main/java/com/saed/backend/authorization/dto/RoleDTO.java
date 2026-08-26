package com.saed.backend.authorization.dto;

public class RoleDTO {
    private String codigo;
    private String alcance;

    public RoleDTO() {}

    public RoleDTO(String codigo, String alcance) {
        this.codigo = codigo;
        this.alcance = alcance;
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getAlcance() { return alcance; }
    public void setAlcance(String alcance) { this.alcance = alcance; }
}
