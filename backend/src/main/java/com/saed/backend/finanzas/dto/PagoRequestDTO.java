package com.saed.backend.finanzas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PagoRequestDTO(
    @NotNull(message = "El idCuota es obligatorio")
    Long idCuota,

    LocalDate fechaPago,

    @NotNull(message = "El valor pagado es obligatorio")
    @DecimalMin(value = "0.01", message = "El valor pagado debe ser mayor que cero")
    BigDecimal valorPagado,

    @NotNull(message = "El método de pago es obligatorio")
    String metodoPago,

    String referencia,
    String comprobanteUrl,
    String notas,
    String idempotencyKey
) {
    // Constructor de compatibilidad para clientes y servicios certificados (ej. WompiServiceImpl)
    public PagoRequestDTO(Long idCuota, LocalDate fechaPago, BigDecimal valorPagado, String metodoPago, String referencia) {
        this(idCuota, fechaPago, valorPagado, metodoPago, referencia, null, null, null);
    }

    public PagoRequestDTO(Long idCuota, BigDecimal valorPagado, String metodoPago, String referencia, LocalDate fechaPago) {
        this(idCuota, fechaPago != null ? fechaPago : LocalDate.now(), valorPagado, metodoPago, referencia, null, null, null);
    }
}
