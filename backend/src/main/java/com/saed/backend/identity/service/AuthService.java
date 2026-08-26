package com.saed.backend.identity.service;

import com.saed.backend.identity.dto.AuthData;
import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.repository.AuthRepository;
import com.saed.backend.security.jwt.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    
    // Dummy hash for timing attack prevention. Represents a valid BCrypt hash structure.
    private final String DUMMY_HASH = "$2a$10$wI8pZ921jI6V/d0QjL6Xm.f8.vj/YdGZ4Y6zO6X1B8zB0aK8L/WKy";

    public AuthService(AuthRepository authRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    public AuthResponse login(LoginRequest request) {
        Optional<AuthData> authDataOpt = authRepository.getAuthData(request.getEmail().toLowerCase().trim());
        
        boolean userExists = authDataOpt.isPresent();
        AuthData authData = authDataOpt.orElse(AuthData.builder().hashPassword(DUMMY_HASH).build());
        
        // Execute matches regardless of existence to prevent timing attacks
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), authData.getHashPassword());
        
        if (!userExists || !passwordMatches) {
            if (userExists) {
                // Register failure in Oracle (handles increments, lockouts, and auditing via autonomous transaction)
                authRepository.registerLoginFailure(authData.getIdUsuario(), "API");
            }
            throw new RuntimeException("Credenciales invalidas"); // Caught by GlobalExceptionHandler -> 401
        }
        
        if ("INACTIVO".equals(authData.getEstado()) || "BLOQUEADO".equals(authData.getEstado())) {
            throw new RuntimeException("Credenciales invalidas"); // Don't reveal blocked status unless policy explicitly says so, but for now use uniform msg
        }

        // Register success in Oracle (resets attempts, updates last login, audits)
        authRepository.registerLoginSuccess(authData.getIdUsuario(), "API");

        String token = jwtProvider.generateIdentityToken(authData.getIdUsuario());
        return new AuthResponse(token, false, authData.getIdUsuario());
    }
}
