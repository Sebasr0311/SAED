package com.saed.backend.pqrs.dto;

public class PqrsSlaConfigDTO {
    private Long idSlaConfig;
    private Long idPropiedad;
    private String prioridad;
    private Integer tiempoMaximoHoras;
    private Integer alertaVencimientoHoras;

    public PqrsSlaConfigDTO() {}

    public PqrsSlaConfigDTO(Long idSlaConfig, Long idPropiedad, String prioridad, Integer tiempoMaximoHoras, Integer alertaVencimientoHoras) {
        this.idSlaConfig = idSlaConfig;
        this.idPropiedad = idPropiedad;
        this.prioridad = prioridad;
        this.tiempoMaximoHoras = tiempoMaximoHoras;
        this.alertaVencimientoHoras = alertaVencimientoHoras;
    }

    public Long getIdSlaConfig() { return idSlaConfig; }
    public void setIdSlaConfig(Long idSlaConfig) { this.idSlaConfig = idSlaConfig; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }

    public Integer getTiempoMaximoHoras() { return tiempoMaximoHoras; }
    public void setTiempoMaximoHoras(Integer tiempoMaximoHoras) { this.tiempoMaximoHoras = tiempoMaximoHoras; }

    public Integer getAlertaVencimientoHoras() { return alertaVencimientoHoras; }
    public void setAlertaVencimientoHoras(Integer alertaVencimientoHoras) { this.alertaVencimientoHoras = alertaVencimientoHoras; }
}
