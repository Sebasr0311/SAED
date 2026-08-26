package com.saed.backend.authorization.dto;

public class RoleDTO {
    private Long idRol;
    private String codigo;
    private String alcance;

    public RoleDTO() {}

    public RoleDTO(String codigo, String alcance) {
        this.codigo = codigo;
        this.alcance = alcance;
    }

    public Long getIdRol() { return idRol; }
    public void setIdRol(Long idRol) { this.idRol = idRol; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getAlcance() { return alcance; }
    public void setAlcance(String alcance) { this.alcance = alcance; }
}
