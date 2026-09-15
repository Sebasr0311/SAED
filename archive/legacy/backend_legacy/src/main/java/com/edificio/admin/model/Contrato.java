package com.edificio.admin.model;

import com.edificio.admin.model.enums.EstadoContrato;
import com.edificio.admin.model.enums.TipoContrato;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Contrato de arrendamiento de un apartamento.
 * Corresponde a la tabla CONTRATOS.
 * fechaFin NULL = contrato indefinido.
 */
public class Contrato {

    private Integer       idContrato;
    private Integer       idApartamento;
    private Integer       idTutor;           // FK -> TUTORES, nullable
    private Integer       idRegistradoPor;   // FK -> USUARIOS (NOT NULL en BD)
    private LocalDate     fechaInicio;
    private LocalDate     fechaFin;       // nullable
    private BigDecimal    valorMensual;
    private int           diaPago = 5;
    private int           diasGracia = 5;
    private BigDecimal    porcentajeMora = new BigDecimal("1.5");
    private String        contratoPdfUrl;
    private TipoContrato  tipoContrato;
    private EstadoContrato estado;
    private String        notas;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private String        numeroApartamento;  // JOIN con APARTAMENTOS
    private String        nombreResidente;    // JOIN con CONTRATO_RESIDENTE + RESIDENTES
    private Integer       idResidente;        // JOIN con CONTRATO_RESIDENTE (rol ARRENDATARIO)

    public Contrato() {}

    // ---- getters / setters ----

    public Integer        getIdContrato()                       { return idContrato; }
    public void           setIdContrato(Integer v)              { this.idContrato = v; }

    public Integer        getIdApartamento()                    { return idApartamento; }
    public void           setIdApartamento(Integer v)           { this.idApartamento = v; }

    public Integer        getIdTutor()                          { return idTutor; }
    public void           setIdTutor(Integer v)                 { this.idTutor = v; }

    public Integer        getIdRegistradoPor()                  { return idRegistradoPor; }
    public void           setIdRegistradoPor(Integer v)         { this.idRegistradoPor = v; }

    public LocalDate      getFechaInicio()                      { return fechaInicio; }
    public void           setFechaInicio(LocalDate v)           { this.fechaInicio = v; }

    public LocalDate      getFechaFin()                         { return fechaFin; }
    public void           setFechaFin(LocalDate v)              { this.fechaFin = v; }

    public BigDecimal     getValorMensual()                     { return valorMensual; }
    public void           setValorMensual(BigDecimal v)         { this.valorMensual = v; }

    public int            getDiaPago()                          { return diaPago; }
    public void           setDiaPago(int v)                     { this.diaPago = v; }

    public int            getDiasGracia()                       { return diasGracia; }
    public void           setDiasGracia(int v)                  { this.diasGracia = v; }

    public BigDecimal     getPorcentajeMora()                   { return porcentajeMora; }
    public void           setPorcentajeMora(BigDecimal v)       { this.porcentajeMora = v; }

    public TipoContrato   getTipoContrato()                     { return tipoContrato; }
    public void           setTipoContrato(TipoContrato v)       { this.tipoContrato = v; }

    public String         getContratoPdfUrl()                   { return contratoPdfUrl; }
    public void           setContratoPdfUrl(String v)           { this.contratoPdfUrl = v; }

    public EstadoContrato getEstado()                           { return estado; }
    public void           setEstado(EstadoContrato v)           { this.estado = v; }

    public String         getNotas()                            { return notas; }
    public void           setNotas(String v)                    { this.notas = v; }

    public LocalDateTime  getCreadoEn()                         { return creadoEn; }
    public void           setCreadoEn(LocalDateTime v)          { this.creadoEn = v; }

    public LocalDateTime  getActualizadoEn()                    { return actualizadoEn; }
    public void           setActualizadoEn(LocalDateTime v)     { this.actualizadoEn = v; }

    public String         getNumeroApartamento()                 { return numeroApartamento; }
    public void           setNumeroApartamento(String v)         { this.numeroApartamento = v; }

    public String         getNombreResidente()                   { return nombreResidente; }
    public void           setNombreResidente(String v)           { this.nombreResidente = v; }

    public Integer        getIdResidente()                       { return idResidente; }
    public void           setIdResidente(Integer v)              { this.idResidente = v; }
}
