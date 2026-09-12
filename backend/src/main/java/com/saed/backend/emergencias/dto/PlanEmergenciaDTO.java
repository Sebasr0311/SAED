package com.saed.backend.emergencias.dto;

import java.time.LocalDate;

public class PlanEmergenciaDTO {
    private Long idPlanEmergencia;
    private Long idPropiedad;
    private String titulo;
    private String tipoContingencia;
    private String puntosEncuentro;
    private String rutasEvacuacionDesc;
    private String mapaEvacuacionUrl;
    private String documentoPlanUrl;
    private LocalDate fechaUltimaRevision;
    private String estado;

    public PlanEmergenciaDTO() {}

    public Long getIdPlanEmergencia() { return idPlanEmergencia; }
    public void setIdPlanEmergencia(Long idPlanEmergencia) { this.idPlanEmergencia = idPlanEmergencia; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

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
