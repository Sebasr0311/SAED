package com.edificio.admin.model;

import com.edificio.admin.model.enums.TipoRol;
import java.time.LocalDateTime;

/**
 * Cuenta de acceso a la aplicacion.
 * Corresponde a la tabla USUARIOS.
 * id_residente es NULL para porteros/admin sin contrato de residencia.
 */
public class Usuario {

    private Integer       idUsuario;
    private Integer       idResidente;   // FK nullable -> RESIDENTES
    private String        username;
    private String        passwordHash;  // BCrypt factor 12
    private TipoRol       rol;
    private boolean       activo;
    private LocalDateTime ultimoLogin;
    private LocalDateTime actualizadoEn;
    private String        nombreResidente;   // JOIN desde RESIDENTES
    private Integer       idApartamento;     // JOIN desde CONTRATOS
    private String        numeroApartamento; // JOIN desde APARTAMENTOS via CONTRATOS

    public Usuario() {}

    public Usuario(Integer idUsuario, Integer idResidente, String username,
                   String passwordHash, TipoRol rol) {
        this.idUsuario    = idUsuario;
        this.idResidente  = idResidente;
        this.username     = username;
        this.passwordHash = passwordHash;
        this.rol          = rol;
        this.activo       = true;
    }

    // ---- getters / setters ----

    public Integer       getIdUsuario()                     { return idUsuario; }
    public void          setIdUsuario(Integer v)            { this.idUsuario = v; }

    public Integer       getIdResidente()                   { return idResidente; }
    public void          setIdResidente(Integer v)          { this.idResidente = v; }

    public String        getUsername()                      { return username; }
    public void          setUsername(String v)              { this.username = v; }

    public String        getPasswordHash()                  { return passwordHash; }
    public void          setPasswordHash(String v)          { this.passwordHash = v; }

    public TipoRol       getRol()                           { return rol; }
    public void          setRol(TipoRol v)                  { this.rol = v; }

    public boolean       isActivo()                         { return activo; }
    public void          setActivo(boolean v)               { this.activo = v; }

    public LocalDateTime getUltimoLogin()                   { return ultimoLogin; }
    public void          setUltimoLogin(LocalDateTime v)    { this.ultimoLogin = v; }

    public LocalDateTime getActualizadoEn()                 { return actualizadoEn; }
    public void          setActualizadoEn(LocalDateTime v)  { this.actualizadoEn = v; }

    public String        getNombreResidente()               { return nombreResidente; }
    public void          setNombreResidente(String v)       { this.nombreResidente = v; }

    public Integer       getIdApartamento()                 { return idApartamento; }
    public void          setIdApartamento(Integer v)        { this.idApartamento = v; }

    public String        getNumeroApartamento()              { return numeroApartamento; }
    public void          setNumeroApartamento(String v)      { this.numeroApartamento = v; }

    @Override
    public String toString() {
        return username + " [" + (rol != null ? rol.name() : "?") + "]";
    }
}
