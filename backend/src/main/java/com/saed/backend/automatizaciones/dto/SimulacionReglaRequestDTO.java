package com.saed.backend.automatizaciones.dto;

public class SimulacionReglaRequestDTO {
    private Long idEntidadOrigen;
    private String tipoEntidadOrigen;
    private String payloadJson;

    public SimulacionReglaRequestDTO() {}

    public SimulacionReglaRequestDTO(Long idEntidadOrigen, String tipoEntidadOrigen, String payloadJson) {
        this.idEntidadOrigen = idEntidadOrigen;
        this.tipoEntidadOrigen = tipoEntidadOrigen;
        this.payloadJson = payloadJson;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idEntidadOrigen;
        private String tipoEntidadOrigen;
        private String payloadJson;

        public Builder idEntidadOrigen(Long idEntidadOrigen) { this.idEntidadOrigen = idEntidadOrigen; return this; }
        public Builder tipoEntidadOrigen(String tipoEntidadOrigen) { this.tipoEntidadOrigen = tipoEntidadOrigen; return this; }
        public Builder payloadJson(String payloadJson) { this.payloadJson = payloadJson; return this; }

        public SimulacionReglaRequestDTO build() {
            return new SimulacionReglaRequestDTO(idEntidadOrigen, tipoEntidadOrigen, payloadJson);
        }
    }

    public Long getIdEntidadOrigen() { return idEntidadOrigen; }
    public void setIdEntidadOrigen(Long idEntidadOrigen) { this.idEntidadOrigen = idEntidadOrigen; }

    public String getTipoEntidadOrigen() { return tipoEntidadOrigen; }
    public void setTipoEntidadOrigen(String tipoEntidadOrigen) { this.tipoEntidadOrigen = tipoEntidadOrigen; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
