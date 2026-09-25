package com.saed.backend.platform.dto;

public class ReenviarCredencialesRequestDTO {
    private String referencia;
    private Long idOrganizacion;
    private Long idUsuario;
    private String emailDestino;
    private Boolean generarNuevaPassword = true;
    private String passwordManual;

    public ReenviarCredencialesRequestDTO() {}

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }

    public String getEmailDestino() { return emailDestino; }
    public void setEmailDestino(String emailDestino) { this.emailDestino = emailDestino; }

    public Boolean getGenerarNuevaPassword() { return generarNuevaPassword; }
    public void setGenerarNuevaPassword(Boolean generarNuevaPassword) { this.generarNuevaPassword = generarNuevaPassword; }

    public String getPasswordManual() { return passwordManual; }
    public void setPasswordManual(String passwordManual) { this.passwordManual = passwordManual; }
}
