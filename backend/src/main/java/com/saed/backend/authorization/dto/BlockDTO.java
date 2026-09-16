package com.saed.backend.authorization.dto;

public class BlockDTO {
    private Long id;
    private Long idPropiedad;
    private Long idBloquePadre;
    private String tipo;
    private String codigo;
    private String nombre;
    private Integer orden;
    private String estado;
    private String bloquePadreNombre;
    private Integer totalHijos;
    private Integer totalUnidades;

    public BlockDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
    public void setOrden(Integer orden) { this.orden = orden; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getBloquePadreNombre() { return bloquePadreNombre; }
    public void setBloquePadreNombre(String bloquePadreNombre) { this.bloquePadreNombre = bloquePadreNombre; }

    public Integer getTotalHijos() { return totalHijos; }
    public void setTotalHijos(Integer totalHijos) { this.totalHijos = totalHijos; }

    public Integer getTotalUnidades() { return totalUnidades; }
    public void setTotalUnidades(Integer totalUnidades) { this.totalUnidades = totalUnidades; }
}
