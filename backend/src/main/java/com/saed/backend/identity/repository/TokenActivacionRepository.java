package com.saed.backend.identity.repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Activation-token persistence. Every operation runs in a validated security-schema procedure (V5.73): the table is
 * protected by a row-level policy, the activation endpoints are unauthenticated, and only the SHA-256 hash of a
 * token ever reaches the database.
 */
public interface TokenActivacionRepository {

    /** Issues a token for an account inside the caller's scope; empty when the database refuses or finds no account. */
    Optional<UsuarioInfo> emitirToken(Long idUsuario, String tipo, String tokenHash, Instant fechaExpiracion, String ip);

    /** Issues a fresh activation token for the account with that user name or e-mail, if any. */
    Optional<UsuarioInfo> emitirTokenReenvio(String identificador, String tokenHash, Instant fechaExpiracion, String ip);

    /** State of a token hash: VALIDO, USADO, EXPIRADO, INEXISTENTE or USUARIO_NO_DISPONIBLE. */
    Consulta consultar(String tokenHash, Instant ahora);

    /**
     * Atomically sets the password hash, activates the account and consumes the token. Returns ACTIVADA, USADO,
     * EXPIRADO, INEXISTENTE or USUARIO_NO_DISPONIBLE.
     */
    String activar(String tokenHash, String hashPassword, Instant ahora);

    /** Reads the account of the authenticated caller (row-level policies apply). */
    Optional<UsuarioInfo> obtenerUsuarioInfo(Long idUsuario);

    record Consulta(String estado, UsuarioInfo usuario, Instant fechaExpiracion) {}

    record UsuarioInfo(
        Long idUsuario,
        String nombreUsuario,
        String email,
        String primerNombre,
        String estado
    ) {}
}
