package com.saed.backend.emergencias.dto;

public class ContactoEmergenciaDTO {
    private Long idContactoEmergencia;
    private Long idPropiedad;
    private String entidad;
    private String tipoServicio;
    private String telefonoPrincipal;
    private String telefonoAlterno;
    private String direccion;
    private String esPrioritarioMinuta;
    private Integer ordenVisualizacion;

    public ContactoEmergenciaDTO() {}

    public Long getIdContactoEmergencia() { return idContactoEmergencia; }
    public void setIdContactoEmergencia(Long idContactoEmergencia) { this.idContactoEmergencia = idContactoEmergencia; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }

    public String getTipoServicio() { return tipoServicio; }
    public void setTipoServicio(String tipoServicio) { this.tipoServicio = tipoServicio; }

    public String getTelefonoPrincipal() { return telefonoPrincipal; }
    public void setTelefonoPrincipal(String telefonoPrincipal) { this.telefonoPrincipal = telefonoPrincipal; }

    public String getTelefonoAlterno() { return telefonoAlterno; }
    public void setTelefonoAlterno(String telefonoAlterno) { this.telefonoAlterno = telefonoAlterno; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getEsPrioritarioMinuta() { return esPrioritarioMinuta; }
    public void setEsPrioritarioMinuta(String esPrioritarioMinuta) { this.esPrioritarioMinuta = esPrioritarioMinuta; }

    public Integer getOrdenVisualizacion() { return ordenVisualizacion; }
    public void setOrdenVisualizacion(Integer ordenVisualizacion) { this.ordenVisualizacion = ordenVisualizacion; }
}
