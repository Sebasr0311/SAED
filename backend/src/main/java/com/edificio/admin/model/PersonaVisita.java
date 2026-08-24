package com.edificio.admin.model;

import java.time.LocalDateTime;

/**
 * Fila de union N:M entre VISITAS y VISITANTES.
 * Corresponde a la tabla REGISTRO_VISITA.
 * esTitular=true => visitante principal del grupo.
 */
public class PersonaVisita {

    private Integer       idRegistroVisita;
    private Integer       idVisita;
    private Integer       idVisitante;
    private boolean       esTitular;
    private Integer       idVehiculoVisita;  // nullable
    private LocalDateTime creadoEn;

    public PersonaVisita() {}

    // ---- getters / setters ----

    public Integer       getIdRegistroVisita()                      { return idRegistroVisita; }
    public void          setIdRegistroVisita(Integer v)             { this.idRegistroVisita = v; }

    public Integer       getIdVisita()                              { return idVisita; }
    public void          setIdVisita(Integer v)                     { this.idVisita = v; }

    public Integer       getIdVisitante()                           { return idVisitante; }
    public void          setIdVisitante(Integer v)                  { this.idVisitante = v; }

    public boolean       isEsTitular()                              { return esTitular; }
    public void          setEsTitular(boolean v)                    { this.esTitular = v; }

    public Integer       getIdVehiculoVisita()                      { return idVehiculoVisita; }
    public void          setIdVehiculoVisita(Integer v)             { this.idVehiculoVisita = v; }

    public LocalDateTime getCreadoEn()                              { return creadoEn; }
    public void          setCreadoEn(LocalDateTime v)               { this.creadoEn = v; }
}
