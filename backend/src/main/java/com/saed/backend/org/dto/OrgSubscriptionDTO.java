package com.saed.backend.org.dto;

import java.time.ZonedDateTime;

public class OrgSubscriptionDTO {
    private Long idMembresia;
    private Long idPlan;
    private String planCodigo;
    private String planNombre;
    private String planDescripcion;
    private Double precioMensualCop;
    private String membresiaEstado;
    private ZonedDateTime fechaInicio;
    private ZonedDateTime fechaFin;
    private String tipoPeriodo;

    // Limits and usage
    private long limitePropiedades;
    private long propiedadesUsadas;
    private double porcentajePropiedades;

    private long limiteUnidades;
    private long unidadesUsadas;
    private double porcentajeUnidades;

    private long limiteUsuarios;
    private long usuariosUsados;
    private double porcentajeUsuarios;

    private long limiteAlmacenamientoGb;

    public OrgSubscriptionDTO() {}

    public Long getIdMembresia() { return idMembresia; }
    public void setIdMembresia(Long idMembresia) { this.idMembresia = idMembresia; }

    public Long getIdPlan() { return idPlan; }
    public void setIdPlan(Long idPlan) { this.idPlan = idPlan; }

    public String getPlanCodigo() { return planCodigo; }
    public void setPlanCodigo(String planCodigo) { this.planCodigo = planCodigo; }

    public String getPlanNombre() { return planNombre; }
    public void setPlanNombre(String planNombre) { this.planNombre = planNombre; }

    public String getPlanDescripcion() { return planDescripcion; }
    public void setPlanDescripcion(String planDescripcion) { this.planDescripcion = planDescripcion; }

    public Double getPrecioMensualCop() { return precioMensualCop; }
    public void setPrecioMensualCop(Double precioMensualCop) { this.precioMensualCop = precioMensualCop; }

    public String getMembresiaEstado() { return membresiaEstado; }
    public void setMembresiaEstado(String membresiaEstado) { this.membresiaEstado = membresiaEstado; }

    public ZonedDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(ZonedDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public ZonedDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(ZonedDateTime fechaFin) { this.fechaFin = fechaFin; }

    public String getTipoPeriodo() { return tipoPeriodo; }
    public void setTipoPeriodo(String tipoPeriodo) { this.tipoPeriodo = tipoPeriodo; }

    public long getLimitePropiedades() { return limitePropiedades; }
    public void setLimitePropiedades(long limitePropiedades) { this.limitePropiedades = limitePropiedades; }

    public long getPropiedadesUsadas() { return propiedadesUsadas; }
    public void setPropiedadesUsadas(long propiedadesUsadas) { this.propiedadesUsadas = propiedadesUsadas; }

    public double getPorcentajePropiedades() { return porcentajePropiedades; }
    public void setPorcentajePropiedades(double porcentajePropiedades) { this.porcentajePropiedades = porcentajePropiedades; }

    public long getLimiteUnidades() { return limiteUnidades; }
    public void setLimiteUnidades(long limiteUnidades) { this.limiteUnidades = limiteUnidades; }

    public long getUnidadesUsadas() { return unidadesUsadas; }
    public void setUnidadesUsadas(long unidadesUsadas) { this.unidadesUsadas = unidadesUsadas; }

    public double getPorcentajeUnidades() { return porcentajeUnidades; }
    public void setPorcentajeUnidades(double porcentajeUnidades) { this.porcentajeUnidades = porcentajeUnidades; }

    public long getLimiteUsuarios() { return limiteUsuarios; }
    public void setLimiteUsuarios(long limiteUsuarios) { this.limiteUsuarios = limiteUsuarios; }

    public long getUsuariosUsados() { return usuariosUsados; }
    public void setUsuariosUsados(long usuariosUsados) { this.usuariosUsados = usuariosUsados; }

    public double getPorcentajeUsuarios() { return porcentajeUsuarios; }
    public void setPorcentajeUsuarios(double porcentajeUsuarios) { this.porcentajeUsuarios = porcentajeUsuarios; }

    public long getLimiteAlmacenamientoGb() { return limiteAlmacenamientoGb; }
    public void setLimiteAlmacenamientoGb(long limiteAlmacenamientoGb) { this.limiteAlmacenamientoGb = limiteAlmacenamientoGb; }
}
