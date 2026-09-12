package com.saed.backend.asambleas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AsistenciaDTO {
    private Long idAsistencia;
    private Long idAsamblea;
    private Long idUnidad;
    private String unidadIdentificador;
    private Long idPersonaAsistente;
    private String nombreAsistente;
    private String documentoAsistente;
    private String esPropietarioDirecto;
    private Long idPoder;
    private BigDecimal coeficientePonderado;
    private OffsetDateTime horaRegistro;
    private OffsetDateTime horaRetiro;

    public AsistenciaDTO() {}

    public AsistenciaDTO(Long idAsistencia, Long idAsamblea, Long idUnidad, String unidadIdentificador,
                         Long idPersonaAsistente, String nombreAsistente, String documentoAsistente,
                         String esPropietarioDirecto, Long idPoder, BigDecimal coeficientePonderado,
                         OffsetDateTime horaRegistro, OffsetDateTime horaRetiro) {
        this.idAsistencia = idAsistencia;
        this.idAsamblea = idAsamblea;
        this.idUnidad = idUnidad;
        this.unidadIdentificador = unidadIdentificador;
        this.idPersonaAsistente = idPersonaAsistente;
        this.nombreAsistente = nombreAsistente;
        this.documentoAsistente = documentoAsistente;
        this.esPropietarioDirecto = esPropietarioDirecto;
        this.idPoder = idPoder;
        this.coeficientePonderado = coeficientePonderado;
        this.horaRegistro = horaRegistro;
        this.horaRetiro = horaRetiro;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idAsistencia;
        private Long idAsamblea;
        private Long idUnidad;
        private String unidadIdentificador;
        private Long idPersonaAsistente;
        private String nombreAsistente;
        private String documentoAsistente;
        private String esPropietarioDirecto;
        private Long idPoder;
        private BigDecimal coeficientePonderado;
        private OffsetDateTime horaRegistro;
        private OffsetDateTime horaRetiro;

        public Builder idAsistencia(Long idAsistencia) { this.idAsistencia = idAsistencia; return this; }
        public Builder idAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; return this; }
        public Builder idUnidad(Long idUnidad) { this.idUnidad = idUnidad; return this; }
        public Builder unidadIdentificador(String unidadIdentificador) { this.unidadIdentificador = unidadIdentificador; return this; }
        public Builder idPersonaAsistente(Long idPersonaAsistente) { this.idPersonaAsistente = idPersonaAsistente; return this; }
        public Builder nombreAsistente(String nombreAsistente) { this.nombreAsistente = nombreAsistente; return this; }
        public Builder documentoAsistente(String documentoAsistente) { this.documentoAsistente = documentoAsistente; return this; }
        public Builder esPropietarioDirecto(String esPropietarioDirecto) { this.esPropietarioDirecto = esPropietarioDirecto; return this; }
        public Builder idPoder(Long idPoder) { this.idPoder = idPoder; return this; }
        public Builder coeficientePonderado(BigDecimal coeficientePonderado) { this.coeficientePonderado = coeficientePonderado; return this; }
        public Builder horaRegistro(OffsetDateTime horaRegistro) { this.horaRegistro = horaRegistro; return this; }
        public Builder horaRetiro(OffsetDateTime horaRetiro) { this.horaRetiro = horaRetiro; return this; }

        public AsistenciaDTO build() {
            return new AsistenciaDTO(idAsistencia, idAsamblea, idUnidad, unidadIdentificador,
                    idPersonaAsistente, nombreAsistente, documentoAsistente, esPropietarioDirecto,
                    idPoder, coeficientePonderado, horaRegistro, horaRetiro);
        }
    }

    public Long getIdAsistencia() { return idAsistencia; }
    public void setIdAsistencia(Long idAsistencia) { this.idAsistencia = idAsistencia; }

    public Long getIdAsamblea() { return idAsamblea; }
    public void setIdAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public String getUnidadIdentificador() { return unidadIdentificador; }
    public void setUnidadIdentificador(String unidadIdentificador) { this.unidadIdentificador = unidadIdentificador; }

    public Long getIdPersonaAsistente() { return idPersonaAsistente; }
    public void setIdPersonaAsistente(Long idPersonaAsistente) { this.idPersonaAsistente = idPersonaAsistente; }

    public String getNombreAsistente() { return nombreAsistente; }
    public void setNombreAsistente(String nombreAsistente) { this.nombreAsistente = nombreAsistente; }

    public String getDocumentoAsistente() { return documentoAsistente; }
    public void setDocumentoAsistente(String documentoAsistente) { this.documentoAsistente = documentoAsistente; }

    public String getEsPropietarioDirecto() { return esPropietarioDirecto; }
    public void setEsPropietarioDirecto(String esPropietarioDirecto) { this.esPropietarioDirecto = esPropietarioDirecto; }

    public Long getIdPoder() { return idPoder; }
    public void setIdPoder(Long idPoder) { this.idPoder = idPoder; }

    public BigDecimal getCoeficientePonderado() { return coeficientePonderado; }
    public void setCoeficientePonderado(BigDecimal coeficientePonderado) { this.coeficientePonderado = coeficientePonderado; }

    public OffsetDateTime getHoraRegistro() { return horaRegistro; }
    public void setHoraRegistro(OffsetDateTime horaRegistro) { this.horaRegistro = horaRegistro; }

    public OffsetDateTime getHoraRetiro() { return horaRetiro; }
    public void setHoraRetiro(OffsetDateTime horaRetiro) { this.horaRetiro = horaRetiro; }
}
