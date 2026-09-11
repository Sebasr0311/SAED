package com.saed.backend.finanzas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class GastoRequestDTO {

    private Long idPresupuesto;

    @NotBlank(message = "La categoria es obligatoria")
    @Size(max = 80, message = "La categoria no puede superar 80 caracteres")
    private String categoria;

    @NotBlank(message = "El beneficiario es obligatorio")
    @Size(max = 150, message = "El beneficiario no puede superar 150 caracteres")
    private String beneficiario;

    @Size(max = 30, message = "El NIT de proveedor no puede superar 30 caracteres")
    private String proveedorNit;

    @Size(max = 500, message = "La justificacion no puede superar 500 caracteres")
    private String justificacion;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    private String fechaGasto;

    private String metodoPago = "EFECTIVO";

    private String estado = "REGISTRADO";

    private String facturaSoporteUrl;

    public GastoRequestDTO() {}

    public Long getIdPresupuesto() { return idPresupuesto; }
    public void setIdPresupuesto(Long idPresupuesto) { this.idPresupuesto = idPresupuesto; }

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
    public void setFacturaSoporteUrl(String facturaSoporteUrl) { this.facturaSoporteUrl = facturaSoporteUrl; }
}
