package com.saed.backend.authorization.dto;

public class PropertyDTO {
    private Long id;
    private Long idOrganizacion;
    private String organizacionNombre;
    private Long idTipoPropiedad;
    private String tipoPropiedadCodigo;
    private String tipoPropiedadNombre;
    private String nombre;
    private String direccion;
    private String ciudad;
    private String tipoOcupacionPredominante;
    private String estado;

    public PropertyDTO() {}

    public PropertyDTO(Long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }
    public String getOrganizacionNombre() { return organizacionNombre; }
    public void setOrganizacionNombre(String organizacionNombre) { this.organizacionNombre = organizacionNombre; }
    public Long getIdTipoPropiedad() { return idTipoPropiedad; }
    public void setIdTipoPropiedad(Long idTipoPropiedad) { this.idTipoPropiedad = idTipoPropiedad; }
    public String getTipoPropiedadCodigo() { return tipoPropiedadCodigo; }
    public void setTipoPropiedadCodigo(String tipoPropiedadCodigo) { this.tipoPropiedadCodigo = tipoPropiedadCodigo; }
    public String getTipoPropiedadNombre() { return tipoPropiedadNombre; }
    public void setTipoPropiedadNombre(String tipoPropiedadNombre) { this.tipoPropiedadNombre = tipoPropiedadNombre; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public String getTipoOcupacionPredominante() { return tipoOcupacionPredominante; }
    public void setTipoOcupacionPredominante(String tipoOcupacionPredominante) { this.tipoOcupacionPredominante = tipoOcupacionPredominante; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}