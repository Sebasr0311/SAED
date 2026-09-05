package com.saed.backend.paquetes.dto;

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
    String codigoRetiroPin,
    ZonedDateTime fechaRecepcion,
    String recibidoPorPortero,
    ZonedDateTime fechaNotificacion,
    ZonedDateTime fechaEntrega,
    String entregadoAPersona,
    String entregadoPorPortero,
    String firmaUrl,
    String estado
) {
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
