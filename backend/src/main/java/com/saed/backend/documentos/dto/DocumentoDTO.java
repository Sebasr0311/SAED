package com.saed.backend.documentos.dto;

import java.time.ZonedDateTime;

public class DocumentoDTO {
    private Long idDocumento;
    private Long idOrganizacion;
    private Long idPropiedad;
    private Long idUnidad;
    private String categoria;
    private String titulo;
    private String descripcion;
    private String esPublicoResidentes;
    private String rolMinimoAcceso;
    private String estado;
    private Long creadoPor;
    private ZonedDateTime fechaCreacion;

    // Fields from latest version (for read/write convenience)
    private String archivoUrl;
    private String archivoNombreOrig;
    private Long archivoTamanoBytes;
    private String archivoMimeType;
    private Integer numeroVersion;

    public Long getIdDocumento() { return idDocumento; }
    public void setIdDocumento(Long idDocumento) { this.idDocumento = idDocumento; }

    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public Long getIdUnidad() { return idUnidad; }
    public void setIdUnidad(Long idUnidad) { this.idUnidad = idUnidad; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getEsPublicoResidentes() { return esPublicoResidentes; }
    public void setEsPublicoResidentes(String esPublicoResidentes) { this.esPublicoResidentes = esPublicoResidentes; }

    public String getRolMinimoAcceso() { return rolMinimoAcceso; }
    public void setRolMinimoAcceso(String rolMinimoAcceso) { this.rolMinimoAcceso = rolMinimoAcceso; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Long getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Long creadoPor) { this.creadoPor = creadoPor; }

    public ZonedDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(ZonedDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getArchivoUrl() { return archivoUrl; }
    public void setArchivoUrl(String archivoUrl) { this.archivoUrl = archivoUrl; }

    public String getArchivoNombreOrig() { return archivoNombreOrig; }
    public void setArchivoNombreOrig(String archivoNombreOrig) { this.archivoNombreOrig = archivoNombreOrig; }

    public Long getArchivoTamanoBytes() { return archivoTamanoBytes; }
    public void setArchivoTamanoBytes(Long archivoTamanoBytes) { this.archivoTamanoBytes = archivoTamanoBytes; }

    public String getArchivoMimeType() { return archivoMimeType; }
    public void setArchivoMimeType(String archivoMimeType) { this.archivoMimeType = archivoMimeType; }

    public Integer getNumeroVersion() { return numeroVersion; }
    public void setNumeroVersion(Integer numeroVersion) { this.numeroVersion = numeroVersion; }
}
