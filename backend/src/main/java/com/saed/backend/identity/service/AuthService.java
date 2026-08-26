package com.saed.backend.identity.service;

import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.model.User;
import com.saed.backend.identity.repository.UserRepository;
import com.saed.backend.security.jwt.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            throw new RuntimeException("Credenciales inválidas"); // Will map to 401 later
        }

        User user = userOpt.get();

        if ("INACTIVO".equals(user.getEstado()) || "BLOQUEADO".equals(user.getEstado())) {
            throw new RuntimeException("Usuario inactivo o bloqueado");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getHashPassword())) {
            int failed = user.getIntentosFallidos() + 1;
            userRepository.updateFailedAttempts(user.getIdUsuario(), failed);
            if (failed >= 5) {
                userRepository.lockUser(user.getIdUsuario());
                throw new RuntimeException("Usuario bloqueado por demasiados intentos");
            }
            throw new RuntimeException("Credenciales inválidas");
        }

        // Successful login
        userRepository.updateFailedAttempts(user.getIdUsuario(), 0);
        userRepository.updateLastLogin(user.getIdUsuario());

        String token = jwtProvider.generateIdentityToken(user.getIdUsuario());
        return new AuthResponse(token);
    }
}
