package com.saed.backend.identity.repository;

import java.time.Instant;
import java.util.Optional;

public interface TokenActivacionRepository {

    Long guardarToken(Long idUsuario, String tokenHash, String tipo, Instant fechaExpiracion, String ip);

    Optional<TokenRegistro> buscarPorHash(String tokenHash);

    void marcarComoUsado(Long idToken);

    void invalidarTokensPrevios(Long idUsuario, String tipo);

    Optional<UsuarioInfo> obtenerUsuarioInfo(Long idUsuario);

    Optional<Long> buscarIdUsuarioPorIdentificador(String identificador);

    void actualizarPasswordYActivar(Long idUsuario, String hashPassword);

    record TokenRegistro(
        Long idToken,
        Long idUsuario,
        String tokenHash,
        String tipo,
        Instant fechaExpiracion,
        boolean usado
    ) {}

    record UsuarioInfo(
        Long idUsuario,
        String nombreUsuario,
        String email,
        String primerNombre,
        String estado
    ) {}
}
