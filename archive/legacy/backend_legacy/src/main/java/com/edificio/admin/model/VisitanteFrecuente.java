package com.edificio.admin.model;

import java.time.LocalDateTime;

/**
 * Visitante frecuente de un residente.
 * Mapeado desde la vista VW_VISITANTES_FRECUENTES.
 * Se puebla automáticamente vía TRG_AUTO_FRECUENTE cada vez que un visitante
 * es registrado para un residente; no se inserta manualmente desde Java.
 */
public class VisitanteFrecuente {

    private int           idFrecuente;
    private int           idResidente;
    private int           idVisitante;
    private String        nombreVisitante;
    private String        documento;
    private int           totalVisitas;
    private LocalDateTime ultimaVisita;

    /** Placa del último vehículo registrado; null si vino a pie. */
    private String        ultimaPlaca;

    /**
     * Tipo de vehículo de la última visita.
     * Valores posibles: 'VEHICULO', 'MOTO', 'BICICLETA', 'OTRO' o null (a pie).
     * Coincide con el enum TipoVehiculo.name().
     */
    private String        ultimoTipoVehiculo;

    /** Descripción personalizada cuando ultimo_tipo_vehiculo = 'OTRO'. */
    private String        ultimaDescripcionTipo;

    private boolean       activo;

    public VisitanteFrecuente() {}

    // ---- getters / setters ----

    public int           getIdFrecuente()                          { return idFrecuente; }
    public void          setIdFrecuente(int v)                     { this.idFrecuente = v; }

    public int           getIdResidente()                          { return idResidente; }
    public void          setIdResidente(int v)                     { this.idResidente = v; }

    public int           getIdVisitante()                          { return idVisitante; }
    public void          setIdVisitante(int v)                     { this.idVisitante = v; }

    public String        getNombreVisitante()                      { return nombreVisitante; }
    public void          setNombreVisitante(String v)              { this.nombreVisitante = v; }

    public String        getDocumento()                            { return documento; }
    public void          setDocumento(String v)                    { this.documento = v; }

    public int           getTotalVisitas()                         { return totalVisitas; }
    public void          setTotalVisitas(int v)                    { this.totalVisitas = v; }

    public LocalDateTime getUltimaVisita()                         { return ultimaVisita; }
    public void          setUltimaVisita(LocalDateTime v)          { this.ultimaVisita = v; }

    public String        getUltimaPlaca()                          { return ultimaPlaca; }
    public void          setUltimaPlaca(String v)                  { this.ultimaPlaca = v; }

    public String        getUltimoTipoVehiculo()                   { return ultimoTipoVehiculo; }
    public void          setUltimoTipoVehiculo(String v)           { this.ultimoTipoVehiculo = v; }

    public String        getUltimaDescripcionTipo()                { return ultimaDescripcionTipo; }
    public void          setUltimaDescripcionTipo(String v)        { this.ultimaDescripcionTipo = v; }

    public boolean       isActivo()                                { return activo; }
    public void          setActivo(boolean v)                      { this.activo = v; }

    /** Nombre + documento para mensajes de error y ChoiceDialog. */
    @Override
    public String toString() {
        return nombreVisitante + " (" + documento + ")";
    }
}
