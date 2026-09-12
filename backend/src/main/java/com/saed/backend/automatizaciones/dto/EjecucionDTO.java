package com.saed.backend.automatizaciones.dto;

import java.time.OffsetDateTime;

public class EjecucionDTO {
    private Long idEjecucion;
    private Long idRegla;
    private String nombreRegla;
    private String codigoEvento;
    private Long idEntidadOrigen;
    private String tipoEntidadOrigen;
    private String resultado;
    private String logDetalle;
    private Integer tiempoMs;
    private OffsetDateTime fechaEjecucion;

    public EjecucionDTO() {}

    public EjecucionDTO(Long idEjecucion, Long idRegla, String nombreRegla, String codigoEvento,
                        Long idEntidadOrigen, String tipoEntidadOrigen, String resultado,
                        String logDetalle, Integer tiempoMs, OffsetDateTime fechaEjecucion) {
        this.idEjecucion = idEjecucion;
        this.idRegla = idRegla;
        this.nombreRegla = nombreRegla;
        this.codigoEvento = codigoEvento;
        this.idEntidadOrigen = idEntidadOrigen;
        this.tipoEntidadOrigen = tipoEntidadOrigen;
        this.resultado = resultado;
        this.logDetalle = logDetalle;
        this.tiempoMs = tiempoMs;
        this.fechaEjecucion = fechaEjecucion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idEjecucion;
        private Long idRegla;
        private String nombreRegla;
        private String codigoEvento;
        private Long idEntidadOrigen;
        private String tipoEntidadOrigen;
        private String resultado;
        private String logDetalle;
        private Integer tiempoMs;
        private OffsetDateTime fechaEjecucion;

        public Builder idEjecucion(Long idEjecucion) { this.idEjecucion = idEjecucion; return this; }
        public Builder idRegla(Long idRegla) { this.idRegla = idRegla; return this; }
        public Builder nombreRegla(String nombreRegla) { this.nombreRegla = nombreRegla; return this; }
        public Builder codigoEvento(String codigoEvento) { this.codigoEvento = codigoEvento; return this; }
        public Builder idEntidadOrigen(Long idEntidadOrigen) { this.idEntidadOrigen = idEntidadOrigen; return this; }
        public Builder tipoEntidadOrigen(String tipoEntidadOrigen) { this.tipoEntidadOrigen = tipoEntidadOrigen; return this; }
        public Builder resultado(String resultado) { this.resultado = resultado; return this; }
        public Builder logDetalle(String logDetalle) { this.logDetalle = logDetalle; return this; }
        public Builder tiempoMs(Integer tiempoMs) { this.tiempoMs = tiempoMs; return this; }
        public Builder fechaEjecucion(OffsetDateTime fechaEjecucion) { this.fechaEjecucion = fechaEjecucion; return this; }

        public EjecucionDTO build() {
            return new EjecucionDTO(idEjecucion, idRegla, nombreRegla, codigoEvento,
                    idEntidadOrigen, tipoEntidadOrigen, resultado, logDetalle, tiempoMs, fechaEjecucion);
        }
    }

    public Long getIdEjecucion() { return idEjecucion; }
    public void setIdEjecucion(Long idEjecucion) { this.idEjecucion = idEjecucion; }

    public Long getIdRegla() { return idRegla; }
    public void setIdRegla(Long idRegla) { this.idRegla = idRegla; }

    public String getNombreRegla() { return nombreRegla; }
    public void setNombreRegla(String nombreRegla) { this.nombreRegla = nombreRegla; }

    public String getCodigoEvento() { return codigoEvento; }
    public void setCodigoEvento(String codigoEvento) { this.codigoEvento = codigoEvento; }

    public Long getIdEntidadOrigen() { return idEntidadOrigen; }
    public void setIdEntidadOrigen(Long idEntidadOrigen) { this.idEntidadOrigen = idEntidadOrigen; }

    public String getTipoEntidadOrigen() { return tipoEntidadOrigen; }
    public void setTipoEntidadOrigen(String tipoEntidadOrigen) { this.tipoEntidadOrigen = tipoEntidadOrigen; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public String getLogDetalle() { return logDetalle; }
    public void setLogDetalle(String logDetalle) { this.logDetalle = logDetalle; }

    public Integer getTiempoMs() { return tiempoMs; }
    public void setTiempoMs(Integer tiempoMs) { this.tiempoMs = tiempoMs; }

    public OffsetDateTime getFechaEjecucion() { return fechaEjecucion; }
    public void setFechaEjecucion(OffsetDateTime fechaEjecucion) { this.fechaEjecucion = fechaEjecucion; }
}
