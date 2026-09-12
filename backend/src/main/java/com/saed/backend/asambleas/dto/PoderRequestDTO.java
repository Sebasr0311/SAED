package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotNull;

public class PoderRequestDTO {

    @NotNull(message = "El id de la unidad es obligatorio")
    private Long idUnidad;

    @NotNull(message = "El id del propietario es obligatorio")
    private Long idPersonaPropietario;

    @NotNull(message = "El id del apoderado es obligatorio")
    private Long idPersonaApoderado;

    private String documentoPoderUrl;

    public PoderRequestDTO() {}

    public PoderRequestDTO(Long idUnidad, Long idPersonaPropietario, Long idPersonaApoderado, String documentoPoderUrl) {
        this.idUnidad = idUnidad;
        this.idPersonaPropietario = idPersonaPropietario;
        this.idPersonaApoderado = idPersonaApoderado;
        this.documentoPoderUrl = documentoPoderUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idUnidad;
        private Long idPersonaPropietario;
        private Long idPersonaApoderado;
        private String documentoPoderUrl;

        public Builder idUnidad(Long idUnidad) { this.idUnidad = idUnidad; return this; }
        public Builder idPersonaPropietario(Long idPersonaPropietario) { this.idPersonaPropietario = idPersonaPropietario; return this; }
        public Builder idPersonaApoderado(Long idPersonaApoderado) { this.idPersonaApoderado = idPersonaApoderado; return this; }
        public Builder documentoPoderUrl(String documentoPoderUrl) { this.documentoPoderUrl = documentoPoderUrl; return this; }

        public PoderRequestDTO build() {
            return new PoderRequestDTO(idUnidad, idPersonaPropietario, idPersonaApoderado, documentoPoderUrl);
        }
    }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public Long getIdPersonaPropietario() { return idPersonaPropietario; }
    public void setIdPersonaPropietario(Long idPersonaPropietario) { this.idPersonaPropietario = idPersonaPropietario; }

    public Long getIdPersonaApoderado() { return idPersonaApoderado; }
    public void setIdPersonaApoderado(Long idPersonaApoderado) { this.idPersonaApoderado = idPersonaApoderado; }

    public String getDocumentoPoderUrl() { return documentoPoderUrl; }
    public void setDocumentoPoderUrl(String documentoPoderUrl) { this.documentoPoderUrl = documentoPoderUrl; }
}
