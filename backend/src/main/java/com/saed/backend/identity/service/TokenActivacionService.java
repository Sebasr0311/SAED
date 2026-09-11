package com.saed.backend.identity.service;

import com.saed.backend.identity.dto.TokenActivacionInfoDTO;

public interface TokenActivacionService {

    String generarYEnviarTokenActivacion(Long idUsuario, String ipSolicitud);

    TokenActivacionInfoDTO validarToken(String tokenPlano);

    void activarCuenta(String tokenPlano, String password, String confirmPassword);

    void solicitarReenvio(String identificador, String ipSolicitud);
}
