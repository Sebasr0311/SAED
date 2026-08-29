package com.saed.backend.identity.service;

import com.saed.backend.identity.dto.AuthData;
import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.AuthUserDTO;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.repository.AuthRepository;
import com.saed.backend.security.jwt.JwtProvider;
import com.saed.backend.security.jwt.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    // Dummy hash for timing attack prevention. Represents a valid BCrypt hash structure.
    private final String DUMMY_HASH = "$2a$10$wI8pZ921jI6V/d0QjL6Xm.f8.vj/YdGZ4Y6zO6X1B8zB0aK8L/WKy";

    public AuthService(AuthRepository authRepository, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, RefreshTokenService refreshTokenService) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse login(LoginRequest request) {
        Optional<AuthData> authDataOpt = authRepository.getAuthData(request.getUsername().toLowerCase().trim());

        boolean userExists = authDataOpt.isPresent();
        AuthData authData = authDataOpt.orElse(AuthData.builder().hashPassword(DUMMY_HASH).build());

        // Execute matches regardless of existence to prevent timing attacks
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), authData.getHashPassword());

        if (!userExists || !passwordMatches) {
            if (userExists) {
                // Register failure in Oracle (handles increments, lockouts, and auditing via autonomous transaction)
                authRepository.registerLoginFailure(authData.getIdUsuario(), "API");
            }
            throw new com.saed.backend.identity.exception.InvalidCredentialsException("Credenciales invalidas"); // Caught by GlobalExceptionHandler -> 401
        }

        if ("INACTIVO".equals(authData.getEstado()) || "BLOQUEADO".equals(authData.getEstado())) {
            throw new com.saed.backend.identity.exception.InvalidCredentialsException("Credenciales invalidas"); // Don't reveal blocked status unless policy explicitly says so, but for now use uniform msg
        }

        // Register success in Oracle (resets attempts, updates last login, audits)
        authRepository.registerLoginSuccess(authData.getIdUsuario(), "API");

        String token = jwtProvider.generateIdentityToken(authData.getIdUsuario());
        String refreshToken = refreshTokenService.createRefreshToken(authData.getIdUsuario());

        // Cargar perfil del usuario (rol, alcance, tenant) para el frontend
        AuthUserDTO usuario = authRepository.getUserProfile(authData.getIdUsuario());
        if (usuario == null) {
            // Usuario sin asignacion activa: perfil minimo para no romper la sesion
            usuario = new AuthUserDTO(authData.getIdUsuario(), request.getUsername().trim(), null, "SIN_ASIGNACION", null, null, null, null);
        }
        return new AuthResponse(token, refreshToken, false, authData.getIdUsuario(), usuario);
    }

    public AuthResponse refresh(String refreshToken) {
        Long userId = refreshTokenService.validateAndConsume(refreshToken);
        if (userId == null) {
            throw new com.saed.backend.identity.exception.InvalidCredentialsException("Refresh token invalido o expirado");
        }

        String newToken = jwtProvider.generateIdentityToken(userId);
        String newRefreshToken = refreshTokenService.createRefreshToken(userId);

        AuthUserDTO usuario = authRepository.getUserProfile(userId);
        if (usuario == null) {
            usuario = new AuthUserDTO(userId, null, null, "SIN_ASIGNACION", null, null, null, null);
        }
        return new AuthResponse(newToken, newRefreshToken, false, userId, usuario);
    }

    public void logout(Long userId) {
        refreshTokenService.invalidateAllForUser(userId);
    }
}