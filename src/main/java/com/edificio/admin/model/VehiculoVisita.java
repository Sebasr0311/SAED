package com.edificio.admin.model;

import com.edificio.admin.model.enums.TipoVehiculo;
import java.time.LocalDateTime;

/**
 * Vehiculo que ingresa con una visita.
 * Corresponde a la tabla VEHICULOS_VISITA.
 * idParqueadero nullable => sin parqueadero asignado.
 * descripcionTipo obligatorio cuando tipo=OTRO.
 */
public class VehiculoVisita {

    private Integer       idVehiculoVisita;
    private Integer       idVisita;
    private String        placa;
    private TipoVehiculo  tipo;
    private String        descripcionTipo;  // obligatorio si tipo=OTRO
    private Integer       idParqueadero;    // nullable
    private LocalDateTime horaSalida;       // nullable
    private LocalDateTime actualizadoEn;

    public VehiculoVisita() {}

    // ---- getters / setters ----

    public Integer       getIdVehiculoVisita()                      { return idVehiculoVisita; }
    public void          setIdVehiculoVisita(Integer v)             { this.idVehiculoVisita = v; }

    public Integer       getIdVisita()                              { return idVisita; }
    public void          setIdVisita(Integer v)                     { this.idVisita = v; }

    public String        getPlaca()                                 { return placa; }
    public void          setPlaca(String v)                         { this.placa = v; }

    public TipoVehiculo  getTipo()                                  { return tipo; }
    public void          setTipo(TipoVehiculo v)                    { this.tipo = v; }

    public String        getDescripcionTipo()                       { return descripcionTipo; }
    public void          setDescripcionTipo(String v)               { this.descripcionTipo = v; }

    public Integer       getIdParqueadero()                         { return idParqueadero; }
    public void          setIdParqueadero(Integer v)                { this.idParqueadero = v; }

    public LocalDateTime getHoraSalida()                            { return horaSalida; }
    public void          setHoraSalida(LocalDateTime v)             { this.horaSalida = v; }

    public LocalDateTime getActualizadoEn()                         { return actualizadoEn; }
    public void          setActualizadoEn(LocalDateTime v)          { this.actualizadoEn = v; }
}
