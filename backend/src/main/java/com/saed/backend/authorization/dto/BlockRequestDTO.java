package com.saed.backend.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BlockRequestDTO {
    private Long idPropiedad;
    private Long idBloquePadre;
    @NotBlank(message = "El tipo de bloque es requerido")
    @Size(max = 20, message = "El tipo no puede superar los 20 caracteres")
    private String tipo;

    @NotBlank(message = "El código es requerido")
    @Size(max = 30, message = "El código no puede superar los 30 caracteres")
    private String codigo;

    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    private Integer orden = 0;
    private String estado = "ACTIVO";

    public BlockRequestDTO() {}

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public Long getIdBloquePadre() { return idBloquePadre; }
    public void setIdBloquePadre(Long idBloquePadre) { this.idBloquePadre = idBloquePadre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden != null ? orden : 0; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado != null ? estado : "ACTIVO"; }
}
