package com.saed.backend.audit;

import java.time.OffsetDateTime;

public class AuditEntry {
    private Long idLog;
    private Long idUsuario;
    private Long idOrganizacion;
    private Long idPropiedad;
    private String accion;
    private String entidad;
    private Long idEntidadAfectada;
    private String ipOrigen;
    private String userAgent;
    private String resultado;
    private String estadoAnterior;
    private String estadoNuevo;
    private OffsetDateTime fechaHora;

    public AuditEntry() {
    }

    public AuditEntry(Long idLog, Long idUsuario, Long idOrganizacion, Long idPropiedad,
                      String accion, String entidad, Long idEntidadAfectada,
                      String ipOrigen, String userAgent, String resultado,
                      String estadoAnterior, String estadoNuevo, OffsetDateTime fechaHora) {
        this.idLog = idLog;
        this.idUsuario = idUsuario;
        this.idOrganizacion = idOrganizacion;
        this.idPropiedad = idPropiedad;
        this.accion = accion;
        this.entidad = entidad;
        this.idEntidadAfectada = idEntidadAfectada;
        this.ipOrigen = ipOrigen;
        this.userAgent = userAgent;
        this.resultado = resultado;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.fechaHora = fechaHora;
    }

    public Long getIdLog() {
        return idLog;
    }

    public void setIdLog(Long idLog) {
        this.idLog = idLog;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public Long getIdOrganizacion() {
        return idOrganizacion;
    }

    public void setIdOrganizacion(Long idOrganizacion) {
        this.idOrganizacion = idOrganizacion;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public Long getIdEntidadAfectada() {
        return idEntidadAfectada;
    }

    public void setIdEntidadAfectada(Long idEntidadAfectada) {
        this.idEntidadAfectada = idEntidadAfectada;
    }

    public String getIpOrigen() {
        return ipOrigen;
    }

    public void setIpOrigen(String ipOrigen) {
        this.ipOrigen = ipOrigen;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(String estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(String estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(OffsetDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
