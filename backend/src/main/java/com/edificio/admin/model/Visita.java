package com.edificio.admin.model;

import com.edificio.admin.model.enums.EstadoVisita;
import java.time.LocalDateTime;

/**
 * Cabecera de cada visita autorizada por un residente.
 * Corresponde a la tabla VISITAS.
 * idResidente es desnormalizacion de CONTRATO_RESIDENTE (coherencia via trigger).
 */
public class Visita {

    private Integer       idVisita;
    private Integer       idContratoRes;
    private Integer       idResidente;
    private LocalDateTime fechaRegistro;
    /** Entre 5 y 60 minutos. */
    private int           tiempoValidezMin;
    /** Entre 1 y 99 personas. */
    private int           cantidadPersonas;
    private EstadoVisita  estado;
    private String        notas;
    private LocalDateTime actualizadoEn;

    // ── Campos de presentación ── (poblados por consultas con JOIN)
    private String nombreVisitante;
    private String numeroApartamento;
    private Integer piso;
    private LocalDateTime fechaVisita;      // hora_entrada del registro de acceso
    private LocalDateTime fechaSalida;      // hora_salida del registro de acceso
    private String documentoVisitante;      // número de documento del visitante
    private String nombreResidente;         // nombre completo del residente que autorizó
    private String codigoParqueadero;       // código del parqueadero asignado
    private String fotoCaptura;             // URL de la foto capturada de la visita
    private String tipoVehiculo;            // tipo de vehículo (CARRO, MOTO, A_PIE, etc.)
    private String placaVehiculo;           // placa del vehículo

    public Visita() {}

    // ---- getters / setters ----

    public Integer       getIdVisita()                          { return idVisita; }
    public void          setIdVisita(Integer v)                 { this.idVisita = v; }

    public Integer       getIdContratoRes()                     { return idContratoRes; }
    public void          setIdContratoRes(Integer v)            { this.idContratoRes = v; }

    public Integer       getIdResidente()                       { return idResidente; }
    public void          setIdResidente(Integer v)              { this.idResidente = v; }

    public LocalDateTime getFechaRegistro()                     { return fechaRegistro; }
    public void          setFechaRegistro(LocalDateTime v)      { this.fechaRegistro = v; }

    public int           getTiempoValidezMin()                  { return tiempoValidezMin; }
    public void          setTiempoValidezMin(int v)             { this.tiempoValidezMin = v; }

    public int           getCantidadPersonas()                  { return cantidadPersonas; }
    public void          setCantidadPersonas(int v)             { this.cantidadPersonas = v; }

    public EstadoVisita  getEstado()                            { return estado; }
    public void          setEstado(EstadoVisita v)              { this.estado = v; }

    public String        getNotas()                             { return notas; }
    public void          setNotas(String v)                     { this.notas = v; }

    public LocalDateTime getActualizadoEn()                     { return actualizadoEn; }
    public void          setActualizadoEn(LocalDateTime v)      { this.actualizadoEn = v; }

    public String        getNombreVisitante()                   { return nombreVisitante; }
    public void          setNombreVisitante(String v)           { this.nombreVisitante = v; }

    public String        getNumeroApartamento()                 { return numeroApartamento; }
    public void          setNumeroApartamento(String v)         { this.numeroApartamento = v; }

    public Integer       getPiso()                              { return piso; }
    public void          setPiso(Integer v)                     { this.piso = v; }

    public LocalDateTime getFechaVisita()                       { return fechaVisita; }
    public void          setFechaVisita(LocalDateTime v)        { this.fechaVisita = v; }

    public LocalDateTime getFechaSalida()                       { return fechaSalida; }
    public void          setFechaSalida(LocalDateTime v)        { this.fechaSalida = v; }

    public String        getDocumentoVisitante()                { return documentoVisitante; }
    public void          setDocumentoVisitante(String v)        { this.documentoVisitante = v; }

    public String        getNombreResidente()                   { return nombreResidente; }
    public void          setNombreResidente(String v)           { this.nombreResidente = v; }

    public String        getCodigoParqueadero()                 { return codigoParqueadero; }
    public void          setCodigoParqueadero(String v)         { this.codigoParqueadero = v; }

    public String        getFotoCaptura()                       { return fotoCaptura; }
    public void          setFotoCaptura(String v)               { this.fotoCaptura = v; }

    public String        getTipoVehiculo()                      { return tipoVehiculo; }
    public void          setTipoVehiculo(String v)              { this.tipoVehiculo = v; }

    public String        getPlacaVehiculo()                     { return placaVehiculo; }
    public void          setPlacaVehiculo(String v)             { this.placaVehiculo = v; }
}
