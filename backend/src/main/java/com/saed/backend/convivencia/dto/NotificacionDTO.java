package com.saed.backend.convivencia.dto;

import java.time.LocalDateTime;

public class NotificacionDTO {
    private Long idMensaje;
    private String tipo;
    private String titulo;
    private String cuerpo;
    private String codigoRetiroPin;
    private String enlaceDestino;
    private LocalDateTime fecha;
    private Boolean leido;

    public Long getIdMensaje() { return this.idMensaje; }
    public void setIdMensaje(Long idMensaje) { this.idMensaje = idMensaje; }
    public String getTipo() { return this.tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getTitulo() { return this.titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getCuerpo() { return this.cuerpo; }
    public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
    public String getCodigoRetiroPin() { return this.codigoRetiroPin; }
    public void setCodigoRetiroPin(String codigoRetiroPin) { this.codigoRetiroPin = codigoRetiroPin; }
    public String getEnlaceDestino() { return this.enlaceDestino; }
    public void setEnlaceDestino(String enlaceDestino) { this.enlaceDestino = enlaceDestino; }
    public LocalDateTime getFecha() { return this.fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public Boolean getLeido() { return this.leido; }
    public void setLeido(Boolean leido) { this.leido = leido; }
}
