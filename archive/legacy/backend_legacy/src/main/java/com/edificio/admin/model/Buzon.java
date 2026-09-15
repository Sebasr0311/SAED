package com.edificio.admin.model;

import java.time.LocalDateTime;

public class Buzon {

    private Integer idMensaje;
    private Integer idApartamento;
    private Integer idVisita;
    private String tipo;
    private String titulo;
    private String cuerpo;
    private String fotoCaptura;
    private String empresaMensajeria;
    private String numeroGuia;
    private boolean leido;
    private LocalDateTime leidoEn;
    private boolean entregado;
    private LocalDateTime entregadoEn;
    private Integer confirmado;
    private LocalDateTime confirmadoEn;
    private Integer creadoPor;
    private LocalDateTime fechaCreacion;
    private String numeroApartamento;
    private String nombreResidente;

    public String getNumeroApartamento() { return numeroApartamento; }
    public void setNumeroApartamento(String v) { this.numeroApartamento = v; }
    public String getNombreResidente() { return nombreResidente; }
    public void setNombreResidente(String v) { this.nombreResidente = v; }

    public Integer getIdMensaje() { return idMensaje; }
    public void setIdMensaje(Integer v) { this.idMensaje = v; }

    public Integer getIdApartamento() { return idApartamento; }
    public void setIdApartamento(Integer v) { this.idApartamento = v; }

    public Integer getIdVisita() { return idVisita; }
    public void setIdVisita(Integer v) { this.idVisita = v; }

    public String getTipo() { return tipo; }
    public void setTipo(String v) { this.tipo = v; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String v) { this.titulo = v; }

    public String getCuerpo() { return cuerpo; }
    public void setCuerpo(String v) { this.cuerpo = v; }

    public String getFotoCaptura() { return fotoCaptura; }
    public void setFotoCaptura(String v) { this.fotoCaptura = v; }

    public String getEmpresaMensajeria() { return empresaMensajeria; }
    public void setEmpresaMensajeria(String v) { this.empresaMensajeria = v; }

    public String getNumeroGuia() { return numeroGuia; }
    public void setNumeroGuia(String v) { this.numeroGuia = v; }

    public boolean isLeido() { return leido; }
    public void setLeido(boolean v) { this.leido = v; }

    public LocalDateTime getLeidoEn() { return leidoEn; }
    public void setLeidoEn(LocalDateTime v) { this.leidoEn = v; }

    public boolean isEntregado() { return entregado; }
    public void setEntregado(boolean v) { this.entregado = v; }

    public LocalDateTime getEntregadoEn() { return entregadoEn; }
    public void setEntregadoEn(LocalDateTime v) { this.entregadoEn = v; }

    public Integer getConfirmado() { return confirmado; }
    public void setConfirmado(Integer v) { this.confirmado = v; }

    public LocalDateTime getConfirmadoEn() { return confirmadoEn; }
    public void setConfirmadoEn(LocalDateTime v) { this.confirmadoEn = v; }

    public Integer getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Integer v) { this.creadoPor = v; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime v) { this.fechaCreacion = v; }

}
