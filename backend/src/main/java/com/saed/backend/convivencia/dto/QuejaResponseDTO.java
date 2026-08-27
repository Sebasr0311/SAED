package com.saed.backend.convivencia.dto;
public class QuejaResponseDTO {
    private String respuesta;
    private String estado;
    private String prioridad;

    public String getRespuesta() { return this.respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public String getEstado() { return this.estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getPrioridad() { return this.prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
}
