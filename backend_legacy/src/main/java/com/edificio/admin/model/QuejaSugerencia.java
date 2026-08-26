package com.edificio.admin.model;

import java.time.LocalDateTime;

public class QuejaSugerencia {

    private Integer idQueja;
    private Integer idApartamento;
    private Integer idMulta;
    private String tipo;
    private String categoria;
    private String titulo;
    private String descripcion;
    private String fotoEvidencia;
    private String estado;
    private String respuestaAdmin;
    private String prioridad;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaRespuesta;
    private Integer creadoPor;
    private Integer respondidoPor;

    // Campos adicionales para JOINs
    private String numeroApartamento;
    private String nombreResidente;
    private String nombreAdmin;

    public QuejaSugerencia() {}

    public Integer getIdQueja() { return idQueja; }
    public void setIdQueja(Integer v) { this.idQueja = v; }

    public Integer getIdApartamento() { return idApartamento; }
    public void setIdApartamento(Integer v) { this.idApartamento = v; }

    public Integer getIdMulta() { return idMulta; }
    public void setIdMulta(Integer v) { this.idMulta = v; }

    public String getTipo() { return tipo; }
    public void setTipo(String v) { this.tipo = v; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String v) { this.categoria = v; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String v) { this.titulo = v; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String v) { this.descripcion = v; }

    public String getFotoEvidencia() { return fotoEvidencia; }
    public void setFotoEvidencia(String v) { this.fotoEvidencia = v; }

    public String getEstado() { return estado; }
    public void setEstado(String v) { this.estado = v; }

    public String getRespuestaAdmin() { return respuestaAdmin; }
    public void setRespuestaAdmin(String v) { this.respuestaAdmin = v; }

    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String v) { this.prioridad = v; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime v) { this.fechaCreacion = v; }

    public LocalDateTime getFechaRespuesta() { return fechaRespuesta; }
    public void setFechaRespuesta(LocalDateTime v) { this.fechaRespuesta = v; }

    public Integer getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Integer v) { this.creadoPor = v; }

    public Integer getRespondidoPor() { return respondidoPor; }
    public void setRespondidoPor(Integer v) { this.respondidoPor = v; }

    public String getNumeroApartamento() { return numeroApartamento; }
    public void setNumeroApartamento(String v) { this.numeroApartamento = v; }

    public String getNombreResidente() { return nombreResidente; }
    public void setNombreResidente(String v) { this.nombreResidente = v; }

    public String getNombreAdmin() { return nombreAdmin; }
    public void setNombreAdmin(String v) { this.nombreAdmin = v; }
}
