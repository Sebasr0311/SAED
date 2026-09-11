package com.saed.backend.finanzas.dto;

import java.time.OffsetDateTime;

public class GastoSoporteHistorialDTO {

    private Long idHistorial;
    private Long idGasto;
    private String facturaSoporteUrl;
    private String archivoNombreOrig;
    private String archivoMimeType;
    private Long archivoTamanoBytes;
    private String archivoSha256;
    private Long reemplazadoPor;
    private String reemplazadoPorNombre;
    private OffsetDateTime fechaReemplazo;
    private String motivoReemplazo;

    public GastoSoporteHistorialDTO() {}

    public Long getIdHistorial() { return idHistorial; }
    public void setIdHistorial(Long idHistorial) { this.idHistorial = idHistorial; }

    public Long getIdGasto() { return idGasto; }
    public void setIdGasto(Long idGasto) { this.idGasto = idGasto; }

    public String getFacturaSoporteUrl() { return facturaSoporteUrl; }
    public void setFacturaSoporteUrl(String facturaSoporteUrl) { this.facturaSoporteUrl = facturaSoporteUrl; }

    public String getArchivoNombreOrig() { return archivoNombreOrig; }
    public void setArchivoNombreOrig(String archivoNombreOrig) { this.archivoNombreOrig = archivoNombreOrig; }

    public String getArchivoMimeType() { return archivoMimeType; }
    public void setArchivoMimeType(String archivoMimeType) { this.archivoMimeType = archivoMimeType; }

    public Long getArchivoTamanoBytes() { return archivoTamanoBytes; }
    public void setArchivoTamanoBytes(Long archivoTamanoBytes) { this.archivoTamanoBytes = archivoTamanoBytes; }

    public String getArchivoSha256() { return archivoSha256; }
    public void setArchivoSha256(String archivoSha256) { this.archivoSha256 = archivoSha256; }

    public Long getReemplazadoPor() { return reemplazadoPor; }
    public void setReemplazadoPor(Long reemplazadoPor) { this.reemplazadoPor = reemplazadoPor; }

    public String getReemplazadoPorNombre() { return reemplazadoPorNombre; }
    public void setReemplazadoPorNombre(String reemplazadoPorNombre) { this.reemplazadoPorNombre = reemplazadoPorNombre; }

    public OffsetDateTime getFechaReemplazo() { return fechaReemplazo; }
    public void setFechaReemplazo(OffsetDateTime fechaReemplazo) { this.fechaReemplazo = fechaReemplazo; }

    public String getMotivoReemplazo() { return motivoReemplazo; }
    public void setMotivoReemplazo(String motivoReemplazo) { this.motivoReemplazo = motivoReemplazo; }
}
