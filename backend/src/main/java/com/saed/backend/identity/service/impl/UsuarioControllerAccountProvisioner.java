package com.saed.backend.identity.service.impl;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.identity.controller.UsuarioController;
import com.saed.backend.person.service.AccountProvisioner;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Reuses the account-creation rules of {@link UsuarioController} (role escalation, scope, duplicates, plan limits)
 * until they are extracted into a service. The controller answers refusals as HTTP responses; here every non-2xx
 * answer becomes an exception so the surrounding transaction rolls back.
 */
@Component
public class UsuarioControllerAccountProvisioner implements AccountProvisioner {

    private final UsuarioController usuarioController;

    public UsuarioControllerAccountProvisioner(@Lazy UsuarioController usuarioController) {
        this.usuarioController = usuarioController;
    }

    @Override
    public Map<String, Object> provision(Map<String, Object> payload) {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = usuarioController.crearUsuario(payload);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            String reason = response.getBody() != null ? response.getBody().getMessage() : null;
            throw new ResponseStatusException(response.getStatusCode(),
                    reason != null ? reason : "No fue posible crear la cuenta de acceso");
        }
        Map<String, Object> data = response.getBody().getData();
        return data != null ? data : Map.of();
    }
}
