package com.edificio.admin.model;

import com.edificio.admin.model.enums.EstadoCuota;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cuota mensual de arriendo o administración generada para un contrato.
 * Corresponde a la tabla CUOTAS_ARRIENDO.
 */
public class CuotaArriendo {

    private Integer       idCuota;
    private Integer       idContrato;
    private int           anio;
    private int           mes;
    private String        tipoCuota;          // ARRIENDO | ADMINISTRACION
    private LocalDate     fechaLimite;
    private BigDecimal    valorBase;
    private BigDecimal    valorMora;
    private BigDecimal    valorTotal;
    private EstadoCuota   estado;
    private LocalDateTime actualizadoEn;
    private String        numeroApartamento;  // JOIN con CONTRATOS + APARTAMENTOS
    private String        nombreResidente;    // JOIN con CONTRATO_RESIDENTE + RESIDENTES
    private BigDecimal    totalPagado;        // Suma de pagos registrados (calculado vía LEFT JOIN PAGOS)
    private BigDecimal    saldoPendiente;     // valorTotal - totalPagado

    public CuotaArriendo() {}

    // ---- getters / setters ----

    public Integer       getIdCuota()                           { return idCuota; }
    public void          setIdCuota(Integer v)                  { this.idCuota = v; }

    public Integer       getIdContrato()                        { return idContrato; }
    public void          setIdContrato(Integer v)               { this.idContrato = v; }

    public int           getAnio()                              { return anio; }
    public void          setAnio(int v)                         { this.anio = v; }

    public int           getMes()                               { return mes; }
    public void          setMes(int v)                          { this.mes = v; }

    public String        getTipoCuota()                         { return tipoCuota; }
    public void          setTipoCuota(String v)                 { this.tipoCuota = v; }

    public LocalDate     getFechaLimite()                       { return fechaLimite; }
    public void          setFechaLimite(LocalDate v)            { this.fechaLimite = v; }

    public BigDecimal    getValorBase()                         { return valorBase; }
    public void          setValorBase(BigDecimal v)             { this.valorBase = v; }

    public BigDecimal    getValorMora()                         { return valorMora; }
    public void          setValorMora(BigDecimal v)             { this.valorMora = v; }

    public BigDecimal    getValorTotal()                        { return valorTotal; }
    public void          setValorTotal(BigDecimal v)            { this.valorTotal = v; }

    public EstadoCuota   getEstado()                            { return estado; }
    public void          setEstado(EstadoCuota v)               { this.estado = v; }

    public LocalDateTime getActualizadoEn()                     { return actualizadoEn; }
    public void          setActualizadoEn(LocalDateTime v)      { this.actualizadoEn = v; }

    public String        getNumeroApartamento()                  { return numeroApartamento; }
    public void          setNumeroApartamento(String v)          { this.numeroApartamento = v; }

    public String        getNombreResidente()                    { return nombreResidente; }
    public void          setNombreResidente(String v)            { this.nombreResidente = v; }

    public BigDecimal    getTotalPagado()                        { return totalPagado; }
    public void          setTotalPagado(BigDecimal v)            { this.totalPagado = v; }

    public BigDecimal    getSaldoPendiente()                     { return saldoPendiente; }
    public void          setSaldoPendiente(BigDecimal v)         { this.saldoPendiente = v; }
}
