package com.edificio.admin.model;

import com.edificio.admin.model.enums.MetodoPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de pago (puede ser parcial) asociado a una cuota de arriendo.
 * Corresponde a la tabla PAGOS.
 */
public class Pago {

    private Integer       idPago;
    private Integer       idCuota;
    private LocalDate     fechaPago;
    private BigDecimal    valorPagado;
    private MetodoPago    metodoPago;
    private String        referencia;
    private String        comprobanteUrl;
    private Integer       registradoPor;  // FK -> USUARIOS
    private String        notas;
    private LocalDateTime creadoEn;

    public Pago() {}

    // ---- getters / setters ----

    public Integer       getIdPago()                            { return idPago; }
    public void          setIdPago(Integer v)                   { this.idPago = v; }

    public Integer       getIdCuota()                           { return idCuota; }
    public void          setIdCuota(Integer v)                  { this.idCuota = v; }

    public LocalDate     getFechaPago()                         { return fechaPago; }
    public void          setFechaPago(LocalDate v)              { this.fechaPago = v; }

    public BigDecimal    getValorPagado()                       { return valorPagado; }
    public void          setValorPagado(BigDecimal v)           { this.valorPagado = v; }

    public MetodoPago    getMetodoPago()                        { return metodoPago; }
    public void          setMetodoPago(MetodoPago v)            { this.metodoPago = v; }

    public String        getReferencia()                        { return referencia; }
    public void          setReferencia(String v)                { this.referencia = v; }

    public String        getComprobanteUrl()                    { return comprobanteUrl; }
    public void          setComprobanteUrl(String v)            { this.comprobanteUrl = v; }

    public Integer       getRegistradoPor()                     { return registradoPor; }
    public void          setRegistradoPor(Integer v)            { this.registradoPor = v; }

    public String        getNotas()                             { return notas; }
    public void          setNotas(String v)                     { this.notas = v; }

    public LocalDateTime getCreadoEn()                          { return creadoEn; }
    public void          setCreadoEn(LocalDateTime v)           { this.creadoEn = v; }
}
