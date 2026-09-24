package com.saed.backend.emergencias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class PlanEmergenciaRequestDTO {

    @NotBlank(message = "El título del plan es obligatorio")
    @Size(max = 150, message = "El título no puede exceder 150 caracteres")
    private String titulo;

    @NotBlank(message = "El tipo de contingencia es obligatorio")
    @Size(max = 40, message = "El tipo de contingencia no puede exceder 40 caracteres")
    @Pattern(
        regexp = "^(?i)(INCENDIO|TERREMOTO|INUNDACION|FUGA_GAS|AMENAZA_SEGURIDAD|GENERAL)$",
        message = "El tipo de contingencia debe ser uno de: INCENDIO, TERREMOTO, INUNDACION, FUGA_GAS, AMENAZA_SEGURIDAD, GENERAL"
    )
    private String tipoContingencia;

    @NotBlank(message = "Los puntos de encuentro son obligatorios")
    @Size(max = 300, message = "Los puntos de encuentro no pueden exceder 300 caracteres")
    private String puntosEncuentro;

    @NotBlank(message = "La descripción de rutas de evacuación es obligatoria")
    private String rutasEvacuacionDesc;

    @Size(max = 500, message = "La URL del mapa no puede exceder 500 caracteres")
    private String mapaEvacuacionUrl;

    @Size(max = 500, message = "La URL del documento no puede exceder 500 caracteres")
    private String documentoPlanUrl;

    private LocalDate fechaUltimaRevision;

    @Size(max = 10, message = "El estado no puede exceder 10 caracteres")
    @Pattern(
        regexp = "^(?i)(ACTIVO|EN_REVISION|OBSOLETO)$",
        message = "El estado debe ser uno de: ACTIVO, EN_REVISION, OBSOLETO"
    )
    private String estado;

    public PlanEmergenciaRequestDTO() {}

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getTipoContingencia() { return tipoContingencia; }
    public void setTipoContingencia(String tipoContingencia) { this.tipoContingencia = tipoContingencia; }

    public String getPuntosEncuentro() { return puntosEncuentro; }
    public void setPuntosEncuentro(String puntosEncuentro) { this.puntosEncuentro = puntosEncuentro; }

    public String getRutasEvacuacionDesc() { return rutasEvacuacionDesc; }
    public void setRutasEvacuacionDesc(String rutasEvacuacionDesc) { this.rutasEvacuacionDesc = rutasEvacuacionDesc; }

    public String getMapaEvacuacionUrl() { return mapaEvacuacionUrl; }
    public void setMapaEvacuacionUrl(String mapaEvacuacionUrl) { this.mapaEvacuacionUrl = mapaEvacuacionUrl; }

    public String getDocumentoPlanUrl() { return documentoPlanUrl; }
    public void setDocumentoPlanUrl(String documentoPlanUrl) { this.documentoPlanUrl = documentoPlanUrl; }

    public LocalDate getFechaUltimaRevision() { return fechaUltimaRevision; }
    public void setFechaUltimaRevision(LocalDate fechaUltimaRevision) { this.fechaUltimaRevision = fechaUltimaRevision; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
