package com.saed.backend.porteria.intercom;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * F07-02 intercom endpoints. Keeps the paths the frontend already uses (/buzon/confirmar-pendiente,
 * /buzon/confirmar, /buzon/resultado-notificar, /porteria/qr/notificar), now backed by persisted requests.
 */
@Tag(name = "Citofonia", description = "Solicitudes de autorizacion de visitantes desde porteria")
@RestController
@RequestMapping("/api/v1")
public class IntercomController {

    private final IntercomService service;

    public IntercomController(IntercomService service) {
        this.service = service;
    }

    @PostMapping("/porteria/qr/notificar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public Map<String, Object> notificar(@RequestBody Map<String, String> body) {
        String token = body.get("codigoQr");
        if (token == null || token.isBlank()) {
            token = body.get("token");
        }
        return service.requestFromQr(token);
    }

    @PostMapping("/porteria/citofonia/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public void cancelar(@PathVariable Long id) {
        service.cancel(id);
    }

    @GetMapping("/buzon/resultado-notificar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public Map<String, Object> resultado(@RequestParam Long idVisita) {
        return service.resultForVisit(idVisita);
    }

    @GetMapping("/buzon/confirmar-pendiente")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public List<Map<String, Object>> pendientes() {
        return service.pendingForCaller();
    }

    @PostMapping("/buzon/confirmar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public void confirmar(@RequestBody Map<String, Object> payload) {
        Long id = toLong(payload.get("idMensaje") != null ? payload.get("idMensaje") : payload.get("idSolicitud"));
        Long answer = toLong(payload.get("confirmado"));
        if (id == null || answer == null || (answer != 0L && answer != 1L)) {
            throw new IllegalArgumentException("idMensaje y confirmado (1 o 0) son obligatorios");
        }
        service.decide(id, answer == 1L);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof Boolean b) {
            return b ? 1L : 0L;
        }
        if (value != null) {
            try {
                return Long.valueOf(value.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
