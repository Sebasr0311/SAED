package com.saed.backend.platform.dto;

public class OnboardingAdminSummaryDTO {
    private String referencia;
    private String fechaRegistro;
    private String nombreOrganizacion;
    private String identificacionFiscal;
    private String tipoPersona; // JURIDICA o NATURAL
    private String tipoDocumento;
    private String numeroDocumento;
    private String adminNombreCompleto;
    private String adminUsername;
    private String adminEmail;
    private String telefono;
    private Long idPlan;
    private String planNombre;
    private String cicloFacturacion;
    private Long montoPesos;
    private String estadoPago; // APROBADO, PENDIENTE, RECHAZADO, PRUEBA
    private String estadoIntencion; // PENDIENTE, COMPLETADA, CANCELADA
    private String estadoCorreo; // ENVIADO, SIMULADO, FALLIDO, PENDIENTE
    private String detalleCorreo;
    private String fechaCorreo;
    private Long idOrganizacion;
    private Long idUsuario;
    private Boolean esPrueba;

    public OnboardingAdminSummaryDTO() {}

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(String fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public String getNombreOrganizacion() { return nombreOrganizacion; }
    public void setNombreOrganizacion(String nombreOrganizacion) { this.nombreOrganizacion = nombreOrganizacion; }

    public String getIdentificacionFiscal() { return identificacionFiscal; }
    public void setIdentificacionFiscal(String identificacionFiscal) { this.identificacionFiscal = identificacionFiscal; }

    public String getTipoPersona() { return tipoPersona; }
    public void setTipoPersona(String tipoPersona) { this.tipoPersona = tipoPersona; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getAdminNombreCompleto() { return adminNombreCompleto; }
    public void setAdminNombreCompleto(String adminNombreCompleto) { this.adminNombreCompleto = adminNombreCompleto; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public Long getIdPlan() { return idPlan; }
    public void setIdPlan(Long idPlan) { this.idPlan = idPlan; }

    public String getPlanNombre() { return planNombre; }
    public void setPlanNombre(String planNombre) { this.planNombre = planNombre; }

    public String getCicloFacturacion() { return cicloFacturacion; }
    public void setCicloFacturacion(String cicloFacturacion) { this.cicloFacturacion = cicloFacturacion; }

    public Long getMontoPesos() { return montoPesos; }
    public void setMontoPesos(Long montoPesos) { this.montoPesos = montoPesos; }

    public String getEstadoPago() { return estadoPago; }
    public void setEstadoPago(String estadoPago) { this.estadoPago = estadoPago; }

    public String getEstadoIntencion() { return estadoIntencion; }
    public void setEstadoIntencion(String estadoIntencion) { this.estadoIntencion = estadoIntencion; }

    public String getEstadoCorreo() { return estadoCorreo; }
    public void setEstadoCorreo(String estadoCorreo) { this.estadoCorreo = estadoCorreo; }

    public String getDetalleCorreo() { return detalleCorreo; }
    public void setDetalleCorreo(String detalleCorreo) { this.detalleCorreo = detalleCorreo; }

    public String getFechaCorreo() { return fechaCorreo; }
    public void setFechaCorreo(String fechaCorreo) { this.fechaCorreo = fechaCorreo; }

    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }

    public Boolean getEsPrueba() { return esPrueba; }
    public void setEsPrueba(Boolean esPrueba) { this.esPrueba = esPrueba; }
}
