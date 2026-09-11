package com.saed.backend.sanciones.dto;

import java.time.LocalDateTime;

public class DescargoDTO {
    private Long idDescargo;
    private Long idSancion;
    private Long idPersonaPresenta;
    private String nombrePresenta;
    private String argumentosDefensa;
    private String pruebasAdjuntasUrl;
    private LocalDateTime fechaPresentacion;
    private Long radicadoPorUsuario;

    public DescargoDTO() {}

    public Long getIdDescargo() { return idDescargo; }
    public void setIdDescargo(Long idDescargo) { this.idDescargo = idDescargo; }

    public Long getIdSancion() { return idSancion; }
    public void setIdSancion(Long idSancion) { this.idSancion = idSancion; }

    public Long getIdPersonaPresenta() { return idPersonaPresenta; }
    public void setIdPersonaPresenta(Long idPersonaPresenta) { this.idPersonaPresenta = idPersonaPresenta; }

    public String getNombrePresenta() { return nombrePresenta; }
    public void setNombrePresenta(String nombrePresenta) { this.nombrePresenta = nombrePresenta; }

    public String getArgumentosDefensa() { return argumentosDefensa; }
    public void setArgumentosDefensa(String argumentosDefensa) { this.argumentosDefensa = argumentosDefensa; }

    public String getPruebasAdjuntasUrl() { return pruebasAdjuntasUrl; }
    public void setPruebasAdjuntasUrl(String pruebasAdjuntasUrl) { this.pruebasAdjuntasUrl = pruebasAdjuntasUrl; }

    public LocalDateTime getFechaPresentacion() { return fechaPresentacion; }
    public void setFechaPresentacion(LocalDateTime fechaPresentacion) { this.fechaPresentacion = fechaPresentacion; }

    public Long getRadicadoPorUsuario() { return radicadoPorUsuario; }
    public void setRadicadoPorUsuario(Long radicadoPorUsuario) { this.radicadoPorUsuario = radicadoPorUsuario; }
}
