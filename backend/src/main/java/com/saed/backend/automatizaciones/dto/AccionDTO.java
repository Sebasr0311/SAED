package com.saed.backend.automatizaciones.dto;

public class AccionDTO {
    private Long idAccion;
    private Long idRegla;
    private String tipoAccion;
    private String parametrosJson;
    private Integer ordenEjecucion;

    public AccionDTO() {}

    public AccionDTO(Long idAccion, Long idRegla, String tipoAccion, String parametrosJson, Integer ordenEjecucion) {
        this.idAccion = idAccion;
        this.idRegla = idRegla;
        this.tipoAccion = tipoAccion;
        this.parametrosJson = parametrosJson;
        this.ordenEjecucion = ordenEjecucion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idAccion;
        private Long idRegla;
        private String tipoAccion;
        private String parametrosJson;
        private Integer ordenEjecucion;

        public Builder idAccion(Long idAccion) { this.idAccion = idAccion; return this; }
        public Builder idRegla(Long idRegla) { this.idRegla = idRegla; return this; }
        public Builder tipoAccion(String tipoAccion) { this.tipoAccion = tipoAccion; return this; }
        public Builder parametrosJson(String parametrosJson) { this.parametrosJson = parametrosJson; return this; }
        public Builder ordenEjecucion(Integer ordenEjecucion) { this.ordenEjecucion = ordenEjecucion; return this; }

        public AccionDTO build() {
            return new AccionDTO(idAccion, idRegla, tipoAccion, parametrosJson, ordenEjecucion);
        }
    }

    public Long getIdAccion() { return idAccion; }
    public void setIdAccion(Long idAccion) { this.idAccion = idAccion; }

    public Long getIdRegla() { return idRegla; }
    public void setIdRegla(Long idRegla) { this.idRegla = idRegla; }

    public String getTipoAccion() { return tipoAccion; }
    public void setTipoAccion(String tipoAccion) { this.tipoAccion = tipoAccion; }

    public String getParametrosJson() { return parametrosJson; }
    public void setParametrosJson(String parametrosJson) { this.parametrosJson = parametrosJson; }

    public Integer getOrdenEjecucion() { return ordenEjecucion; }
    public void setOrdenEjecucion(Integer ordenEjecucion) { this.ordenEjecucion = ordenEjecucion; }
}
