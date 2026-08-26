package com.edificio.admin.model;

import java.time.LocalDateTime;

public class Visitante extends Persona {

    private String        email;
    private boolean       activo;
    private LocalDateTime actualizadoEn;

    public Visitante() { super(); }

    public Visitante(Integer idVisitante, Integer idTipoDoc, String numeroDocumento,
                     String nombres, String apellidos, String telefono) {
        super(idVisitante, idTipoDoc, numeroDocumento, nombres, apellidos, telefono);
        this.activo = true;
    }

    public String        getEmail()                         { return email; }
    public void          setEmail(String v)                 { this.email = v; }

    public boolean       isActivo()                         { return activo; }
    public void          setActivo(boolean v)               { this.activo = v; }

    public LocalDateTime getActualizadoEn()                 { return actualizadoEn; }
    public void          setActualizadoEn(LocalDateTime v)  { this.actualizadoEn = v; }

}
