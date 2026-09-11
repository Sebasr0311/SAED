package com.saed.backend.finanzas.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GastoResponseDTO {

    private Long idGasto;
    private Long idPropiedad;
    private String nombrePropiedad;
    private Long idPresupuesto;
    private String rubroPresupuesto;
    private String categoria;
    private String beneficiario;
    private String proveedorNit;
    private String justificacion;
    private BigDecimal monto;
    private String fechaGasto;
    private String metodoPago;
    private String estado;
    private String facturaSoporteUrl;
    private String archivoNombreOrig;
    private String archivoMimeType;
    private Long archivoTamanoBytes;
    private String archivoSha256;
    private Long registradoPor;
    private String registradoPorNombre;
    private String fechaCreacion;
    private Long modificadoPor;
    private String modificadoPorNombre;
    private String fechaModificacion;
    private boolean tieneSoporte;
    private List<GastoSoporteHistorialDTO> historialSoportes = new ArrayList<>();

    public GastoResponseDTO() {}

    public Long getIdGasto() { return idGasto; }
    public void setIdGasto(Long idGasto) { this.idGasto = idGasto; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getNombrePropiedad() { return nombrePropiedad; }
    public void setNombrePropiedad(String nombrePropiedad) { this.nombrePropiedad = nombrePropiedad; }

    public Long getIdPresupuesto() { return idPresupuesto; }
    public void setIdPresupuesto(Long idPresupuesto) { this.idPresupuesto = idPresupuesto; }

    public String getRubroPresupuesto() { return rubroPresupuesto; }
    public void setRubroPresupuesto(String rubroPresupuesto) { this.rubroPresupuesto = rubroPresupuesto; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getBeneficiario() { return beneficiario; }
    public void setBeneficiario(String beneficiario) { this.beneficiario = beneficiario; }

    public String getProveedorNit() { return proveedorNit; }
    public void setProveedorNit(String proveedorNit) { this.proveedorNit = proveedorNit; }

    public String getJustificacion() { return justificacion; }
    public void setJustificacion(String justificacion) { this.justificacion = justificacion; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getFechaGasto() { return fechaGasto; }
    public void setFechaGasto(String fechaGasto) { this.fechaGasto = fechaGasto; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getFacturaSoporteUrl() { return facturaSoporteUrl; }
    public void setFacturaSoporteUrl(String facturaSoporteUrl) {
        this.facturaSoporteUrl = facturaSoporteUrl;
        this.tieneSoporte = facturaSoporteUrl != null && !facturaSoporteUrl.isBlank();
    }

    public String getArchivoNombreOrig() { return archivoNombreOrig; }
    public void setArchivoNombreOrig(String archivoNombreOrig) { this.archivoNombreOrig = archivoNombreOrig; }

    public String getArchivoMimeType() { return archivoMimeType; }
    public void setArchivoMimeType(String archivoMimeType) { this.archivoMimeType = archivoMimeType; }

    public Long getArchivoTamanoBytes() { return archivoTamanoBytes; }
    public void setArchivoTamanoBytes(Long archivoTamanoBytes) { this.archivoTamanoBytes = archivoTamanoBytes; }

    public String getArchivoSha256() { return archivoSha256; }
    public void setArchivoSha256(String archivoSha256) { this.archivoSha256 = archivoSha256; }

    public Long getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Long registradoPor) { this.registradoPor = registradoPor; }

    public String getRegistradoPorNombre() { return registradoPorNombre; }
    public void setRegistradoPorNombre(String registradoPorNombre) { this.registradoPorNombre = registradoPorNombre; }

    public String getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(String fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Long getModificadoPor() { return modificadoPor; }
    public void setModificadoPor(Long modificadoPor) { this.modificadoPor = modificadoPor; }

    public String getModificadoPorNombre() { return modificadoPorNombre; }
    public void setModificadoPorNombre(String modificadoPorNombre) { this.modificadoPorNombre = modificadoPorNombre; }

    public String getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(String fechaModificacion) { this.fechaModificacion = fechaModificacion; }

    public boolean isTieneSoporte() { return tieneSoporte; }
    public void setTieneSoporte(boolean tieneSoporte) { this.tieneSoporte = tieneSoporte; }

    public List<GastoSoporteHistorialDTO> getHistorialSoportes() { return historialSoportes; }
    public void setHistorialSoportes(List<GastoSoporteHistorialDTO> historialSoportes) {
        this.historialSoportes = historialSoportes;
    }
}
