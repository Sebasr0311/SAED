package com.edificio.admin.model;

import com.edificio.admin.model.enums.EstadoMulta;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Multa {

    private Integer idMulta;
    private Integer idApartamento;
    private Integer idMensaje;
    private String tipo;
    private BigDecimal monto;
    private EstadoMulta estado;
    private String descripcion;
    private String fotoEvidencia;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaPago;
    private Integer creadoPor;
    private Integer registradoPagoPor;
    private String metodoPago;

    private String numeroApartamento;
    private String nombreResidente;
    private String nombrePortero;
    private LocalDateTime fechaAvisoRuido;

    public Multa() {}

    public Integer getIdMulta() { return idMulta; }
    public void setIdMulta(Integer v) { this.idMulta = v; }

    public Integer getIdApartamento() { return idApartamento; }
    public void setIdApartamento(Integer v) { this.idApartamento = v; }

    public Integer getIdMensaje() { return idMensaje; }
    public void setIdMensaje(Integer v) { this.idMensaje = v; }

    public String getTipo() { return tipo; }
    public void setTipo(String v) { this.tipo = v; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal v) { this.monto = v; }

    public EstadoMulta getEstado() { return estado; }
    public void setEstado(EstadoMulta v) { this.estado = v; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String v) { this.descripcion = v; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime v) { this.fechaCreacion = v; }

    public LocalDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDateTime v) { this.fechaPago = v; }

    public Integer getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Integer v) { this.creadoPor = v; }

    public Integer getRegistradoPagoPor() { return registradoPagoPor; }
    public void setRegistradoPagoPor(Integer v) { this.registradoPagoPor = v; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String v) { this.metodoPago = v; }

    public String getFotoEvidencia() { return fotoEvidencia; }
    public void setFotoEvidencia(String v) { this.fotoEvidencia = v; }

    public String getNumeroApartamento() { return numeroApartamento; }
    public void setNumeroApartamento(String v) { this.numeroApartamento = v; }

    public String getNombreResidente() { return nombreResidente; }
    public void setNombreResidente(String v) { this.nombreResidente = v; }

    public String getNombrePortero() { return nombrePortero; }
    public void setNombrePortero(String v) { this.nombrePortero = v; }

    public LocalDateTime getFechaAvisoRuido() { return fechaAvisoRuido; }
    public void setFechaAvisoRuido(LocalDateTime v) { this.fechaAvisoRuido = v; }
}
