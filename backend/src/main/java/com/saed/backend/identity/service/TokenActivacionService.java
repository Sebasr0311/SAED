package com.saed.backend.identity.service;

import com.saed.backend.identity.dto.TokenActivacionInfoDTO;

public interface TokenActivacionService {

    /** Issues a single-use token (hash stored only) and mails the activation link; the token is never returned. */
    void generarYEnviarTokenActivacion(Long idUsuario, String ipSolicitud);

    TokenActivacionInfoDTO validarToken(String tokenPlano);

    void activarCuenta(String tokenPlano, String password, String confirmPassword);

    void solicitarReenvio(String identificador, String ipSolicitud);
}
