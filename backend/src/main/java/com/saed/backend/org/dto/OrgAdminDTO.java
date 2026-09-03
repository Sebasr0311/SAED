package com.saed.backend.org.dto;

import java.time.ZonedDateTime;

public class OrgAdminDTO {
    private Long idUsuario;
    private String nombreUsuario;
    private String email;
    private String usuarioEstado;
    private String primerNombre;
    private String primerApellido;
    private String telefono;
    private Long idAsignacion;
    private Long idRol;
    private String rolCodigo;
    private String rolNombre;
    private Long idPropiedad;
    private String propiedadNombre;
    private String asignacionEstado;
    private ZonedDateTime fechaInicio;
    private ZonedDateTime fechaFin;

    public OrgAdminDTO() {}

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsuarioEstado() { return usuarioEstado; }
    public void setUsuarioEstado(String usuarioEstado) { this.usuarioEstado = usuarioEstado; }

    public String getPrimerNombre() { return primerNombre; }
    public void setPrimerNombre(String primerNombre) { this.primerNombre = primerNombre; }

    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String primerApellido) { this.primerApellido = primerApellido; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public Long getIdAsignacion() { return idAsignacion; }
    public void setIdAsignacion(Long idAsignacion) { this.idAsignacion = idAsignacion; }

    public Long getIdRol() { return idRol; }
    public void setIdRol(Long idRol) { this.idRol = idRol; }

    public String getRolCodigo() { return rolCodigo; }
    public void setRolCodigo(String rolCodigo) { this.rolCodigo = rolCodigo; }

    public String getRolNombre() { return rolNombre; }
    public void setRolNombre(String rolNombre) { this.rolNombre = rolNombre; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getPropiedadNombre() { return propiedadNombre; }
    public void setPropiedadNombre(String propiedadNombre) { this.propiedadNombre = propiedadNombre; }

    public String getAsignacionEstado() { return asignacionEstado; }
    public void setAsignacionEstado(String asignacionEstado) { this.asignacionEstado = asignacionEstado; }

    public ZonedDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(ZonedDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public ZonedDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(ZonedDateTime fechaFin) { this.fechaFin = fechaFin; }
}
