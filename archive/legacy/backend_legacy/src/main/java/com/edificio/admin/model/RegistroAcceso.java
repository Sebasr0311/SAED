package com.edificio.admin.model;

import java.time.LocalDateTime;

/**
 * Registro de entrada / salida física del edificio.
 * Corresponde a la tabla REGISTROS_ACCESO (relación 1:1 con VISITAS).
 *
 * El INSERT es realizado exclusivamente por SP_VALIDAR_QR cuando
 * el QR es válido.  Para registrar la salida se actualiza hora_salida;
 * el trigger TRG_ACCESO_SALIDA entonces:
 *   · Cambia VISITAS.estado → FINALIZADA
 *   · Libera parqueaderos
 *   · Copia hora_salida a VEHICULOS_VISITA
 */
public class RegistroAcceso {

    private Integer       idAcceso;
    private Integer       idVisita;
    private Integer       idVigilante;
    private LocalDateTime horaEntrada;
    private LocalDateTime horaSalida;    // NULL = visitante aún dentro
    private String        observaciones;
    private LocalDateTime fechaRegistro;
    private LocalDateTime actualizadoEn;

    // ── Campos de presentación ── (poblados por consultas con JOIN, no columnas propias)
    /** Nombre completo del residente que autorizó la visita. */
    private String nombreResidente;
    private String numeroApartamento;
    private Integer piso;
    /** Código del parqueadero asignado (null si no aplica). */
    private String codigoParqueadero;

    // ---- getters / setters ----

    public Integer       getIdAcceso()                          { return idAcceso; }
    public void          setIdAcceso(Integer v)                 { this.idAcceso = v; }

    public Integer       getIdVisita()                          { return idVisita; }
    public void          setIdVisita(Integer v)                 { this.idVisita = v; }

    public Integer       getIdVigilante()                       { return idVigilante; }
    public void          setIdVigilante(Integer v)              { this.idVigilante = v; }

    public LocalDateTime getHoraEntrada()                       { return horaEntrada; }
    public void          setHoraEntrada(LocalDateTime v)        { this.horaEntrada = v; }

    public LocalDateTime getHoraSalida()                        { return horaSalida; }
    public void          setHoraSalida(LocalDateTime v)         { this.horaSalida = v; }

    public String        getObservaciones()                     { return observaciones; }
    public void          setObservaciones(String v)             { this.observaciones = v; }

    public LocalDateTime getFechaRegistro()                     { return fechaRegistro; }
    public void          setFechaRegistro(LocalDateTime v)      { this.fechaRegistro = v; }

    public LocalDateTime getActualizadoEn()                     { return actualizadoEn; }
    public void          setActualizadoEn(LocalDateTime v)      { this.actualizadoEn = v; }

    public String        getNombreResidente()                   { return nombreResidente; }
    public void          setNombreResidente(String v)           { this.nombreResidente = v; }

    public String        getNumeroApartamento()                 { return numeroApartamento; }
    public void          setNumeroApartamento(String v)         { this.numeroApartamento = v; }

    public Integer       getPiso()                              { return piso; }
    public void          setPiso(Integer v)                     { this.piso = v; }

    public String        getCodigoParqueadero()                 { return codigoParqueadero; }
    public void          setCodigoParqueadero(String v)         { this.codigoParqueadero = v; }
}
