package com.saed.backend.asambleas.dto;

import java.time.OffsetDateTime;

public class PoderDTO {
    private Long idPoder;
    private Long idAsamblea;
    private Long idUnidad;
    private String unidadIdentificador;
    private Long idPersonaPropietario;
    private String nombrePropietario;
    private String documentoPropietario;
    private Long idPersonaApoderado;
    private String nombreApoderado;
    private String documentoApoderado;
    private String documentoPoderUrl;
    private String estado;
    private Long validadoPor;
    private OffsetDateTime fechaRegistro;

    public PoderDTO() {}

    public PoderDTO(Long idPoder, Long idAsamblea, Long idUnidad, String unidadIdentificador,
                    Long idPersonaPropietario, String nombrePropietario, String documentoPropietario,
                    Long idPersonaApoderado, String nombreApoderado, String documentoApoderado,
                    String documentoPoderUrl, String estado, Long validadoPor, OffsetDateTime fechaRegistro) {
        this.idPoder = idPoder;
        this.idAsamblea = idAsamblea;
        this.idUnidad = idUnidad;
        this.unidadIdentificador = unidadIdentificador;
        this.idPersonaPropietario = idPersonaPropietario;
        this.nombrePropietario = nombrePropietario;
        this.documentoPropietario = documentoPropietario;
        this.idPersonaApoderado = idPersonaApoderado;
        this.nombreApoderado = nombreApoderado;
        this.documentoApoderado = documentoApoderado;
        this.documentoPoderUrl = documentoPoderUrl;
        this.estado = estado;
        this.validadoPor = validadoPor;
        this.fechaRegistro = fechaRegistro;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idPoder;
        private Long idAsamblea;
        private Long idUnidad;
        private String unidadIdentificador;
        private Long idPersonaPropietario;
        private String nombrePropietario;
        private String documentoPropietario;
        private Long idPersonaApoderado;
        private String nombreApoderado;
        private String documentoApoderado;
        private String documentoPoderUrl;
        private String estado;
        private Long validadoPor;
        private OffsetDateTime fechaRegistro;

        public Builder idPoder(Long idPoder) { this.idPoder = idPoder; return this; }
        public Builder idAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; return this; }
        public Builder idUnidad(Long idUnidad) { this.idUnidad = idUnidad; return this; }
        public Builder unidadIdentificador(String unidadIdentificador) { this.unidadIdentificador = unidadIdentificador; return this; }
        public Builder idPersonaPropietario(Long idPersonaPropietario) { this.idPersonaPropietario = idPersonaPropietario; return this; }
        public Builder nombrePropietario(String nombrePropietario) { this.nombrePropietario = nombrePropietario; return this; }
        public Builder documentoPropietario(String documentoPropietario) { this.documentoPropietario = documentoPropietario; return this; }
        public Builder idPersonaApoderado(Long idPersonaApoderado) { this.idPersonaApoderado = idPersonaApoderado; return this; }
        public Builder nombreApoderado(String nombreApoderado) { this.nombreApoderado = nombreApoderado; return this; }
        public Builder documentoApoderado(String documentoApoderado) { this.documentoApoderado = documentoApoderado; return this; }
        public Builder documentoPoderUrl(String documentoPoderUrl) { this.documentoPoderUrl = documentoPoderUrl; return this; }
        public Builder estado(String estado) { this.estado = estado; return this; }
        public Builder validadoPor(Long validadoPor) { this.validadoPor = validadoPor; return this; }
        public Builder fechaRegistro(OffsetDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; return this; }

        public PoderDTO build() {
            return new PoderDTO(idPoder, idAsamblea, idUnidad, unidadIdentificador,
                    idPersonaPropietario, nombrePropietario, documentoPropietario,
                    idPersonaApoderado, nombreApoderado, documentoApoderado,
                    documentoPoderUrl, estado, validadoPor, fechaRegistro);
        }
    }

    public Long getIdPoder() { return idPoder; }
    public void setIdPoder(Long idPoder) { this.idPoder = idPoder; }

    public Long getIdAsamblea() { return idAsamblea; }
    public void setIdAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public String getUnidadIdentificador() { return unidadIdentificador; }
    public void setUnidadIdentificador(String unidadIdentificador) { this.unidadIdentificador = unidadIdentificador; }

    public Long getIdPersonaPropietario() { return idPersonaPropietario; }
    public void setIdPersonaPropietario(Long idPersonaPropietario) { this.idPersonaPropietario = idPersonaPropietario; }

    public String getNombrePropietario() { return nombrePropietario; }
    public void setNombrePropietario(String nombrePropietario) { this.nombrePropietario = nombrePropietario; }

    public String getDocumentoPropietario() { return documentoPropietario; }
    public void setDocumentoPropietario(String documentoPropietario) { this.documentoPropietario = documentoPropietario; }

    public Long getIdPersonaApoderado() { return idPersonaApoderado; }
    public void setIdPersonaApoderado(Long idPersonaApoderado) { this.idPersonaApoderado = idPersonaApoderado; }

    public String getNombreApoderado() { return nombreApoderado; }
    public void setNombreApoderado(String nombreApoderado) { this.nombreApoderado = nombreApoderado; }

    public String getDocumentoApoderado() { return documentoApoderado; }
    public void setDocumentoApoderado(String documentoApoderado) { this.documentoApoderado = documentoApoderado; }

    public String getDocumentoPoderUrl() { return documentoPoderUrl; }
    public void setDocumentoPoderUrl(String documentoPoderUrl) { this.documentoPoderUrl = documentoPoderUrl; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Long getValidadoPor() { return validadoPor; }
    public void setValidadoPor(Long validadoPor) { this.validadoPor = validadoPor; }

    public OffsetDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(OffsetDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
}
