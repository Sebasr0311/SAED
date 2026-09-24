package com.saed.backend.incidentes.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class IncidenteDTO {
    private Long idIncidente;
    private Long idPropiedad;
    private Long idPorteria;
    private Long idZonaComun;
    private Long idUnidad;
    
    @NotNull
    @Size(max = 150)
    private String titulo;
    
    @NotNull
    @Size(max = 40)
    private String tipoIncidente;
    
    @Size(max = 15)
    private String nivelSeveridad;
    
    @NotNull
    private String descripcionHechos;
    
    @NotNull
    private ZonedDateTime fechaHoraIncidente;
    
    private Long registradoPor;
    private String requirioAutoridades; // 'S' o 'N'
    private String entidadAutoridad;
    private String numeroDenunciaPolicia;
    private String evidenciasUrls;
    private String accionesInmediatas;
    private String estado;
    private ZonedDateTime fechaCierre;
    private String conclusionesCierre;
    private ZonedDateTime fechaRegistro;

    // Pipeline de investigación
    private Long investigadoPor;
    private ZonedDateTime fechaInicioInvestigacion;
    private ZonedDateTime fechaFinInvestigacion;
    private String hallazgosInvestigacion;

    // Pipeline de escalamiento
    private Long escaladoPor;
    private ZonedDateTime fechaEscalamiento;
    private String motivoEscalamiento;
    private String sancionSugerida;

    // Personas/vehículos involucrados
    private List<IncidenteInvolucradoDTO> involucrados = new ArrayList<>();

    // Visual helpers
    private String nombreRegistradoPor;
    private String unidadIdentificador;

    public IncidenteDTO() {}

    // Getters and Setters
    public Long getIdIncidente() { return idIncidente; }
    public void setIdIncidente(Long idIncidente) { this.idIncidente = idIncidente; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public Long getIdPorteria() { return idPorteria; }
    public void setIdPorteria(Long idPorteria) { this.idPorteria = idPorteria; }

    public Long getIdZonaComun() { return idZonaComun; }
    public void setIdZonaComun(Long idZonaComun) { this.idZonaComun = idZonaComun; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getTipoIncidente() { return tipoIncidente; }
    public void setTipoIncidente(String tipoIncidente) { this.tipoIncidente = tipoIncidente; }

    public String getNivelSeveridad() { return nivelSeveridad; }
    public void setNivelSeveridad(String nivelSeveridad) { this.nivelSeveridad = nivelSeveridad; }

    public String getDescripcionHechos() { return descripcionHechos; }
    public void setDescripcionHechos(String descripcionHechos) { this.descripcionHechos = descripcionHechos; }

    public ZonedDateTime getFechaHoraIncidente() { return fechaHoraIncidente; }
    public void setFechaHoraIncidente(ZonedDateTime fechaHoraIncidente) { this.fechaHoraIncidente = fechaHoraIncidente; }

    public Long getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Long registradoPor) { this.registradoPor = registradoPor; }

    public String getRequirioAutoridades() { return requirioAutoridades; }
    public void setRequirioAutoridades(String requirioAutoridades) { this.requirioAutoridades = requirioAutoridades; }

    public String getEntidadAutoridad() { return entidadAutoridad; }
    public void setEntidadAutoridad(String entidadAutoridad) { this.entidadAutoridad = entidadAutoridad; }

    public String getNumeroDenunciaPolicia() { return numeroDenunciaPolicia; }
    public void setNumeroDenunciaPolicia(String numeroDenunciaPolicia) { this.numeroDenunciaPolicia = numeroDenunciaPolicia; }

    public String getEvidenciasUrls() { return evidenciasUrls; }
    public void setEvidenciasUrls(String evidenciasUrls) { this.evidenciasUrls = evidenciasUrls; }

    public String getAccionesInmediatas() { return accionesInmediatas; }
    public void setAccionesInmediatas(String accionesInmediatas) { this.accionesInmediatas = accionesInmediatas; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public ZonedDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(ZonedDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public String getConclusionesCierre() { return conclusionesCierre; }
    public void setConclusionesCierre(String conclusionesCierre) { this.conclusionesCierre = conclusionesCierre; }

    public ZonedDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(ZonedDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public Long getInvestigadoPor() { return investigadoPor; }
    public void setInvestigadoPor(Long investigadoPor) { this.investigadoPor = investigadoPor; }

    public ZonedDateTime getFechaInicioInvestigacion() { return fechaInicioInvestigacion; }
    public void setFechaInicioInvestigacion(ZonedDateTime fechaInicioInvestigacion) { this.fechaInicioInvestigacion = fechaInicioInvestigacion; }

    public ZonedDateTime getFechaFinInvestigacion() { return fechaFinInvestigacion; }
    public void setFechaFinInvestigacion(ZonedDateTime fechaFinInvestigacion) { this.fechaFinInvestigacion = fechaFinInvestigacion; }

    public String getHallazgosInvestigacion() { return hallazgosInvestigacion; }
    public void setHallazgosInvestigacion(String hallazgosInvestigacion) { this.hallazgosInvestigacion = hallazgosInvestigacion; }

    public Long getEscaladoPor() { return escaladoPor; }
    public void setEscaladoPor(Long escaladoPor) { this.escaladoPor = escaladoPor; }

    public ZonedDateTime getFechaEscalamiento() { return fechaEscalamiento; }
    public void setFechaEscalamiento(ZonedDateTime fechaEscalamiento) { this.fechaEscalamiento = fechaEscalamiento; }

    public String getMotivoEscalamiento() { return motivoEscalamiento; }
    public void setMotivoEscalamiento(String motivoEscalamiento) { this.motivoEscalamiento = motivoEscalamiento; }

    public String getSancionSugerida() { return sancionSugerida; }
    public void setSancionSugerida(String sancionSugerida) { this.sancionSugerida = sancionSugerida; }

    public List<IncidenteInvolucradoDTO> getInvolucrados() { return involucrados; }
    public void setInvolucrados(List<IncidenteInvolucradoDTO> involucrados) { this.involucrados = involucrados; }

    public String getNombreRegistradoPor() { return nombreRegistradoPor; }
    public void setNombreRegistradoPor(String nombreRegistradoPor) { this.nombreRegistradoPor = nombreRegistradoPor; }

    public String getUnidadIdentificador() { return unidadIdentificador; }
    public void setUnidadIdentificador(String unidadIdentificador) { this.unidadIdentificador = unidadIdentificador; }
}
