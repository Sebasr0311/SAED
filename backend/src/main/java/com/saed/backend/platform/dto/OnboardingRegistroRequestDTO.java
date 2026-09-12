package com.saed.backend.platform.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class OnboardingRegistroRequestDTO {

    @NotBlank(message = "El nombre de la organización o conjunto es obligatorio")
    @Size(max = 150)
    private String nombreOrganizacion;

    @NotBlank(message = "La identificación fiscal o NIT es obligatoria")
    @Size(max = 30)
    @JsonAlias({"nit", "identificacion_fiscal"})
    private String identificacionFiscal;

    @NotBlank(message = "El correo de la organización es obligatorio")
    @Email(message = "Formato de correo inválido")
    @Size(max = 150)
    @JsonAlias({"emailContacto", "email_organizacion", "email_contacto"})
    private String emailOrganizacion;

    @Size(max = 30)
    @JsonAlias({"telefonoContacto", "telefono_organizacion", "telefono_contacto"})
    private String telefonoOrganizacion;

    @Size(max = 80)
    private String ciudad;

    @Size(max = 200)
    private String direccion;

    @NotBlank(message = "El nombre del administrador es obligatorio")
    @Size(max = 80)
    @JsonAlias({"adminPrimerNombre", "primer_nombre", "admin_primer_nombre"})
    private String primerNombre;

    @NotBlank(message = "El apellido del administrador es obligatorio")
    @Size(max = 80)
    @JsonAlias({"adminPrimerApellido", "primer_apellido", "admin_primer_apellido"})
    private String primerApellido;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 30)
    @JsonAlias({"adminNumeroDocumento", "numero_documento", "admin_numero_documento"})
    private String numeroDocumento;

    @NotBlank(message = "El correo del administrador es obligatorio")
    @Email(message = "Formato de correo de administrador inválido")
    @Size(max = 150)
    @JsonAlias({"adminEmail", "email_admin"})
    private String emailAdmin;

    @Size(max = 30)
    @JsonAlias({"adminTelefono", "telefono_admin"})
    private String telefonoAdmin;

    @NotNull(message = "Debe seleccionar un plan de suscripción")
    private Long idPlan;

    private String cicloFacturacion = "MENSUAL";

    private Boolean esPrueba = false;

    // Getters and Setters
    public String getNombreOrganizacion() { return nombreOrganizacion; }
    public void setNombreOrganizacion(String nombreOrganizacion) { this.nombreOrganizacion = nombreOrganizacion; }

    public String getIdentificacionFiscal() { return identificacionFiscal; }
    public void setIdentificacionFiscal(String identificacionFiscal) { this.identificacionFiscal = identificacionFiscal; }
    public void setNit(String nit) { this.identificacionFiscal = nit; }

    public String getEmailOrganizacion() { return emailOrganizacion; }
    public void setEmailOrganizacion(String emailOrganizacion) { this.emailOrganizacion = emailOrganizacion; }
    public void setEmailContacto(String emailContacto) { this.emailOrganizacion = emailContacto; }

    public String getTelefonoOrganizacion() { return telefonoOrganizacion; }
    public void setTelefonoOrganizacion(String telefonoOrganizacion) { this.telefonoOrganizacion = telefonoOrganizacion; }
    public void setTelefonoContacto(String telefonoContacto) { this.telefonoOrganizacion = telefonoContacto; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getPrimerNombre() { return primerNombre; }
    public void setPrimerNombre(String primerNombre) { this.primerNombre = primerNombre; }
    public void setAdminPrimerNombre(String adminPrimerNombre) { this.primerNombre = adminPrimerNombre; }

    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String primerApellido) { this.primerApellido = primerApellido; }
    public void setAdminPrimerApellido(String adminPrimerApellido) { this.primerApellido = adminPrimerApellido; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }
    public void setAdminNumeroDocumento(String adminNumeroDocumento) { this.numeroDocumento = adminNumeroDocumento; }

    public String getEmailAdmin() { return emailAdmin; }
    public void setEmailAdmin(String emailAdmin) { this.emailAdmin = emailAdmin; }
    public void setAdminEmail(String adminEmail) { this.emailAdmin = adminEmail; }

    public String getTelefonoAdmin() { return telefonoAdmin; }
    public void setTelefonoAdmin(String telefonoAdmin) { this.telefonoAdmin = telefonoAdmin; }
    public void setAdminTelefono(String adminTelefono) { this.telefonoAdmin = adminTelefono; }

    public void setAdminTipoDocumento(String adminTipoDocumento) {}

    public Long getIdPlan() { return idPlan; }
    public void setIdPlan(Long idPlan) { this.idPlan = idPlan; }

    public String getCicloFacturacion() { return cicloFacturacion; }
    public void setCicloFacturacion(String cicloFacturacion) { this.cicloFacturacion = cicloFacturacion; }

    public Boolean getEsPrueba() { return esPrueba != null ? esPrueba : false; }
    public void setEsPrueba(Boolean esPrueba) { this.esPrueba = esPrueba; }
}
