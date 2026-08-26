package com.edificio.admin.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Intencion de pago con Wompi (tabla TRANSACCIONES_PAGO).
 * Cada fila representa UN intento de pago; el estado se sincroniza con el
 * webhook de Wompi y/o la reconciliacion periodica.
 */
public class WompiPago {

    private Integer       id;
    private String        referencia;
    private Integer       idApartamento;
    private Integer       idUsuario;
    private String        concepto;            // CUOTA | MULTA
    private Integer       idCuota;             // nullable, segun concepto
    private Integer       idMulta;             // nullable, segun concepto
    private long          montoCentavos;       // centavos COP (API Wompi)
    private String        moneda;              // COP
    private String        estado;              // PENDIENTE|APROBADO|RECHAZADO|VENCIDO|ERROR
    private String        idTransaccionWompi;  // ID asignado por el widget de Wompi
    private String        metodoPagoWompi;     // CARD | NEQUI | PSE | BANCOLOMBIA ...
    private String        payloadWebhook;      // evento crudo (auditoria)
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaConfirmacion;

    public WompiPago() {}

    public Integer getId() { return id; }
    public void setId(Integer v) { this.id = v; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String v) { this.referencia = v; }

    public Integer getIdApartamento() { return idApartamento; }
    public void setIdApartamento(Integer v) { this.idApartamento = v; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer v) { this.idUsuario = v; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String v) { this.concepto = v; }

    public Integer getIdCuota() { return idCuota; }
    public void setIdCuota(Integer v) { this.idCuota = v; }

    public Integer getIdMulta() { return idMulta; }
    public void setIdMulta(Integer v) { this.idMulta = v; }

    public long getMontoCentavos() { return montoCentavos; }
    public void setMontoCentavos(long v) { this.montoCentavos = v; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String v) { this.moneda = v; }

    public String getEstado() { return estado; }
    public void setEstado(String v) { this.estado = v; }

    public String getIdTransaccionWompi() { return idTransaccionWompi; }
    public void setIdTransaccionWompi(String v) { this.idTransaccionWompi = v; }

    public String getMetodoPagoWompi() { return metodoPagoWompi; }
    public void setMetodoPagoWompi(String v) { this.metodoPagoWompi = v; }

    public String getPayloadWebhook() { return payloadWebhook; }
    public void setPayloadWebhook(String v) { this.payloadWebhook = v; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime v) { this.fechaCreacion = v; }

    public LocalDateTime getFechaConfirmacion() { return fechaConfirmacion; }
    public void setFechaConfirmacion(LocalDateTime v) { this.fechaConfirmacion = v; }

    /** Monto en pesos (el sistema trabaja en decimales; Wompi en centavos). */
    public BigDecimal montoEnPesos() {
        return new java.math.BigDecimal(montoCentavos).movePointLeft(2);
    }
}
