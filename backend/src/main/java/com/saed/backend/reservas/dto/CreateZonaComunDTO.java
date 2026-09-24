package com.saed.backend.reservas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO para la creación de zonas comunes en una copropiedad (GAP-F8-05).
 * El idPropiedad siempre se resuelve desde el contexto de sesión autenticado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateZonaComunDTO {

    @NotBlank(message = "El nombre de la zona común es obligatorio.")
    @Size(min = 1, max = 100, message = "El nombre no puede exceder los 100 caracteres.")
    private String nombre;

    @NotBlank(message = "El tipo de zona común es obligatorio.")
    @Size(max = 30, message = "El tipo no puede exceder los 30 caracteres.")
    private String tipo;

    @Positive(message = "El aforo máximo debe ser un número entero mayor a 0.")
    private Integer aforoMaximo;

    private Object requiereReserva = "S";

    @DecimalMin(value = "0.00", message = "El costo de reserva debe ser mayor o igual a 0.")
    private BigDecimal costoReserva = BigDecimal.ZERO;

    @Pattern(regexp = "^(ACTIVA|MANTENIMIENTO|INACTIVA)$", message = "El estado debe ser ACTIVA, MANTENIMIENTO o INACTIVA.")
    private String estado = "ACTIVA";

    // Atributo protegido: ignorado en persistencia (anti-IDOR forzado desde SaedContextHolder)
    private Long idPropiedad;

    public CreateZonaComunDTO() {}

    public CreateZonaComunDTO(String nombre, String tipo, Integer aforoMaximo, Object requiereReserva, BigDecimal costoReserva, String estado) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.aforoMaximo = aforoMaximo;
        this.requiereReserva = requiereReserva;
        this.costoReserva = costoReserva;
        this.estado = estado;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getAforoMaximo() {
        return aforoMaximo;
    }

    public void setAforoMaximo(Integer aforoMaximo) {
        this.aforoMaximo = aforoMaximo;
    }

    public Object getRequiereReserva() {
        return requiereReserva;
    }

    public void setRequiereReserva(Object requiereReserva) {
        this.requiereReserva = requiereReserva;
    }

    public String getRequiereReservaDb() {
        if (requiereReserva == null) return "S";
        if (requiereReserva instanceof Boolean b) {
            return b ? "S" : "N";
        }
        String str = requiereReserva.toString().trim();
        if ("true".equalsIgnoreCase(str) || "S".equalsIgnoreCase(str) || "1".equals(str)) return "S";
        if ("false".equalsIgnoreCase(str) || "N".equalsIgnoreCase(str) || "0".equals(str)) return "N";
        return "S";
    }

    public BigDecimal getCostoReserva() {
        return costoReserva;
    }

    public void setCostoReserva(BigDecimal costoReserva) {
        this.costoReserva = costoReserva;
    }

    public String getEstado() {
        return estado != null ? estado : "ACTIVA";
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }
}
