package com.saed.backend.convivencia.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MultaDTO {
    private Long idMulta;
    private String numeroApartamento;
    private String nombreResidente;
    private String tipo;
    private BigDecimal monto;
    private String estado;
    private LocalDateTime fechaCreacion;

    public Long getIdMulta() { return this.idMulta; }
    public void setIdMulta(Long idMulta) { this.idMulta = idMulta; }
    public String getNumeroApartamento() { return this.numeroApartamento; }
    public void setNumeroApartamento(String numeroApartamento) { this.numeroApartamento = numeroApartamento; }
    public String getNombreResidente() { return this.nombreResidente; }
    public void setNombreResidente(String nombreResidente) { this.nombreResidente = nombreResidente; }
    public String getTipo() { return this.tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public BigDecimal getMonto() { return this.monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getEstado() { return this.estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFechaCreacion() { return this.fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
