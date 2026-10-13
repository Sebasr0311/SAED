package com.saed.backend.paquetes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;

public record PaqueteDTO(
    Long idPaquete,
    Long idPropiedad,
    Long idPorteria,
    Long idUnidad,
    String numeroApartamento,
    Long idPersonaDestinatario,
    String nombreDestinatario,
    String empresaMensajeria,
    String numeroGuia,
    String descripcion,
    String tamano,
    String fotoPaqueteUrl,
    // Pickup PIN: only the recipient resident may receive it; omitted from JSON when redacted.
    @JsonInclude(JsonInclude.Include.NON_NULL) String codigoRetiroPin,
    ZonedDateTime fechaRecepcion,
    String recibidoPorPortero,
    ZonedDateTime fechaNotificacion,
    ZonedDateTime fechaEntrega,
    String entregadoAPersona,
    String entregadoPorPortero,
    String firmaUrl,
    String estado
) {
    /** Copy without the pickup PIN, for any caller that is not the recipient resident. */
    public PaqueteDTO withoutPin() {
        return new PaqueteDTO(idPaquete, idPropiedad, idPorteria, idUnidad, numeroApartamento,
                idPersonaDestinatario, nombreDestinatario, empresaMensajeria, numeroGuia, descripcion,
                tamano, fotoPaqueteUrl, null, fechaRecepcion, recibidoPorPortero, fechaNotificacion,
                fechaEntrega, entregadoAPersona, entregadoPorPortero, firmaUrl, estado);
    }

    @JsonProperty("idMensaje")
    public Long idMensaje() {
        return idPaquete;
    }

    @JsonProperty("nombreResidente")
    public String nombreResidente() {
        return nombreDestinatario != null && !nombreDestinatario.isBlank() ? nombreDestinatario : "Residente";
    }

    @JsonProperty("titulo")
    public String titulo() {
        return descripcion != null && !descripcion.isBlank() ? descripcion : "Paquete en portería";
    }

    @JsonProperty("fechaCreacion")
    public ZonedDateTime fechaCreacion() {
        return fechaRecepcion;
    }

    @JsonProperty("entregado")
    public boolean entregado() {
        return "ENTREGADO".equalsIgnoreCase(estado);
    }

    @JsonProperty("fotoCaptura")
    public String fotoCaptura() {
        return fotoPaqueteUrl;
    }

    @JsonProperty("tipo")
    public String tipo() {
        return "PAQUETE";
    }
}
