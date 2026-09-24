package com.saed.backend.reglamentos.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public class ReglamentoDTO {
    private Long idReglamento;
    private Long idOrganizacion;
    private Long idPropiedad;
    private String tipoNormativa;
    private String titulo;
    private String descripcion;
    private Long idDocumento;
    private String estado;
    private LocalDate fechaEntradaEnVigor;
    private ZonedDateTime fechaPublicacion;
    private Long creadoPor;
    private Long publicadoPor;
    private ZonedDateTime fechaCreacion;
    private ZonedDateTime fechaModificacion;

    // Document metadata joined from DOCUMENTOS / VERSIONES_DOCUMENTO
    private String archivoNombreOrig;
    private Long archivoTamanoBytes;
    private String archivoMimeType;
    private String archivoSha256;
    private Integer numeroVersion;
    private String nombrePropiedad;

    public Long getIdReglamento() { return idReglamento; }
    public void setIdReglamento(Long idReglamento) { this.idReglamento = idReglamento; }

    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getTipoNormativa() { return tipoNormativa; }
    public void setTipoNormativa(String tipoNormativa) { this.tipoNormativa = tipoNormativa; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Long getIdDocumento() { return idDocumento; }
    public void setIdDocumento(Long idDocumento) { this.idDocumento = idDocumento; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDate getFechaEntradaEnVigor() { return fechaEntradaEnVigor; }
    public void setFechaEntradaEnVigor(LocalDate fechaEntradaEnVigor) { this.fechaEntradaEnVigor = fechaEntradaEnVigor; }

    public ZonedDateTime getFechaPublicacion() { return fechaPublicacion; }
    public void setFechaPublicacion(ZonedDateTime fechaPublicacion) { this.fechaPublicacion = fechaPublicacion; }

    public Long getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Long creadoPor) { this.creadoPor = creadoPor; }

    public Long getPublicadoPor() { return publicadoPor; }
    public void setPublicadoPor(Long publicadoPor) { this.publicadoPor = publicadoPor; }

    public ZonedDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(ZonedDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public ZonedDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(ZonedDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }

    public String getArchivoNombreOrig() { return archivoNombreOrig; }
    public void setArchivoNombreOrig(String archivoNombreOrig) { this.archivoNombreOrig = archivoNombreOrig; }

    public Long getArchivoTamanoBytes() { return archivoTamanoBytes; }
    public void setArchivoTamanoBytes(Long archivoTamanoBytes) { this.archivoTamanoBytes = archivoTamanoBytes; }

    public String getArchivoMimeType() { return archivoMimeType; }
    public void setArchivoMimeType(String archivoMimeType) { this.archivoMimeType = archivoMimeType; }

    public String getArchivoSha256() { return archivoSha256; }
    public void setArchivoSha256(String archivoSha256) { this.archivoSha256 = archivoSha256; }

    public Integer getNumeroVersion() { return numeroVersion; }
    public void setNumeroVersion(Integer numeroVersion) { this.numeroVersion = numeroVersion; }

    public String getNombrePropiedad() { return nombrePropiedad; }
    public void setNombrePropiedad(String nombrePropiedad) { this.nombrePropiedad = nombrePropiedad; }
}
