package com.saed.backend.identity.model;

import java.time.ZonedDateTime;

public class User {
    private Long idUsuario;
    private Long idPersona;
    private String nombreUsuario;
    private String email;
    private String hashPassword;
    private String estado;
    private Integer intentosFallidos;
    private ZonedDateTime fechaBloqueo;
    private ZonedDateTime ultimoLogin;
    private String requiereCambioPassword;

    // Getters and Setters
    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
    
    public Long getIdPersona() { return idPersona; }
    public void setIdPersona(Long idPersona) { this.idPersona = idPersona; }
    
    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getHashPassword() { return hashPassword; }
    public void setHashPassword(String hashPassword) { this.hashPassword = hashPassword; }
    
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    
    public Integer getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(Integer intentosFallidos) { this.intentosFallidos = intentosFallidos; }

    public ZonedDateTime getFechaBloqueo() { return fechaBloqueo; }
    public void setFechaBloqueo(ZonedDateTime fechaBloqueo) { this.fechaBloqueo = fechaBloqueo; }
    
    public ZonedDateTime getUltimoLogin() { return ultimoLogin; }
    public void setUltimoLogin(ZonedDateTime ultimoLogin) { this.ultimoLogin = ultimoLogin; }
    
    public String getRequiereCambioPassword() { return requiereCambioPassword; }
    public void setRequiereCambioPassword(String requiereCambioPassword) { this.requiereCambioPassword = requiereCambioPassword; }
}
