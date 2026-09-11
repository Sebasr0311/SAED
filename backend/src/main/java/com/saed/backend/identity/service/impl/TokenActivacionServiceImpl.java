package com.saed.backend.identity.service.impl;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.identity.dto.TokenActivacionInfoDTO;
import com.saed.backend.identity.repository.TokenActivacionRepository;
import com.saed.backend.identity.service.TokenActivacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TokenActivacionServiceImpl implements TokenActivacionService {

    private static final Logger log = LoggerFactory.getLogger(TokenActivacionServiceImpl.class);
    private static final Duration EXPIRACION_TOKEN = Duration.ofHours(48);
    private static final Pattern PWD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!._*-]).{8,64}$");

    private final TokenActivacionRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final String frontendUrl;

    public TokenActivacionServiceImpl(
            TokenActivacionRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Override
    @Transactional
    public String generarYEnviarTokenActivacion(Long idUsuario, String ipSolicitud) {
        if (idUsuario == null) {
            throw new IllegalArgumentException("El idUsuario es requerido");
        }

        // 1. Invalidar tokens previos de activación no usados
        tokenRepository.invalidarTokensPrevios(idUsuario, "ACTIVACION_INICIAL");

        // 2. Generar token criptográfico aleatorio de 32 bytes (256 bits)
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);

        // 3. Almacenar el hash SHA-256 en base de datos (zero-knowledge)
        String tokenHash = calcularSha256(rawToken);
        Instant fechaExpiracion = Instant.now().plus(EXPIRACION_TOKEN);

        tokenRepository.guardarToken(idUsuario, tokenHash, "ACTIVACION_INICIAL", fechaExpiracion, ipSolicitud);
        log.info("Token de activación generado para idUsuario={}, expira={}", idUsuario, fechaExpiracion);

        // 4. Enviar correo electrónico con enlace de activación
        tokenRepository.obtenerUsuarioInfo(idUsuario).ifPresent(user -> {
            if (user.email() != null && !user.email().isBlank()) {
                String enlace = frontendUrl + "/activar-cuenta?token=" + rawToken;
                enviarCorreoBienvenidaActivacion(user.email(), user.primerNombre(), user.nombreUsuario(), enlace);
            }
        });

        return rawToken;
    }

    @Override
    public TokenActivacionInfoDTO validarToken(String tokenPlano) {
        if (tokenPlano == null || tokenPlano.isBlank()) {
            return TokenActivacionInfoDTO.invalido("El token de activación no fue proporcionado.");
        }

        String tokenHash = calcularSha256(tokenPlano.trim());
        Optional<TokenActivacionRepository.TokenRegistro> optReg = tokenRepository.buscarPorHash(tokenHash);

        if (optReg.isEmpty()) {
            return TokenActivacionInfoDTO.invalido("El enlace de activación no es válido o no existe.");
        }

        TokenActivacionRepository.TokenRegistro reg = optReg.get();

        if (reg.usado()) {
            return TokenActivacionInfoDTO.invalido("Este enlace de activación ya ha sido utilizado previamente.");
        }

        if (reg.fechaExpiracion().isBefore(Instant.now())) {
            return TokenActivacionInfoDTO.invalido("El enlace de activación ha expirado. Solicite un nuevo enlace.");
        }

        Optional<TokenActivacionRepository.UsuarioInfo> optUser = tokenRepository.obtenerUsuarioInfo(reg.idUsuario());
        if (optUser.isEmpty()) {
            return TokenActivacionInfoDTO.invalido("No se encontró el usuario asociado a este enlace.");
        }

        TokenActivacionRepository.UsuarioInfo user = optUser.get();
        return TokenActivacionInfoDTO.valido(
                user.idUsuario(),
                user.nombreUsuario(),
                user.email(),
                user.primerNombre(),
                reg.fechaExpiracion()
        );
    }

    @Override
    @Transactional
    public void activarCuenta(String tokenPlano, String password, String confirmPassword) {
        if (tokenPlano == null || tokenPlano.isBlank()) {
            throw new IllegalArgumentException("Token de activación no proporcionado");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La contraseña es requerida");
        }

        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }

        if (!PWD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                "La contraseña debe contener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial (@#$%^&+=!._*-)."
            );
        }

        String tokenHash = calcularSha256(tokenPlano.trim());
        TokenActivacionRepository.TokenRegistro reg = tokenRepository.buscarPorHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("El enlace de activación no es válido"));

        if (reg.usado()) {
            throw new IllegalStateException("Este enlace de activación ya fue utilizado");
        }

        if (reg.fechaExpiracion().isBefore(Instant.now())) {
            throw new IllegalStateException("El enlace de activación ha expirado");
        }

        // 1. Hashear con BCrypt y actualizar usuario a estado ACTIVO
        String hashPassword = passwordEncoder.encode(password);
        tokenRepository.actualizarPasswordYActivar(reg.idUsuario(), hashPassword);

        // 2. Marcar token como usado
        tokenRepository.marcarComoUsado(reg.idToken());

        log.info("Cuenta activada exitosamente para idUsuario={}", reg.idUsuario());
    }

    @Override
    public void solicitarReenvio(String identificador, String ipSolicitud) {
        if (identificador == null || identificador.isBlank()) {
            return;
        }

        tokenRepository.buscarIdUsuarioPorIdentificador(identificador.trim()).ifPresent(idUsuario -> {
            try {
                generarYEnviarTokenActivacion(idUsuario, ipSolicitud);
            } catch (Exception ex) {
                log.error("Error al reenviar token de activación para idUsuario={}: {}", idUsuario, ex.getMessage());
            }
        });
    }

    private void enviarCorreoBienvenidaActivacion(String email, String nombre, String username, String enlace) {
        try {
            String asunto = "¡Bienvenido a SAED! Activa tu cuenta de acceso";
            String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #0A1628; color: #E2E8F0; margin: 0; padding: 20px; }
                    .card { max-width: 580px; margin: 0 auto; background: #0F213A; border: 1px solid #1E3A5F; border-radius: 12px; padding: 32px; box-shadow: 0 4px 20px rgba(0,0,0,0.4); }
                    .header { text-align: center; border-bottom: 1px solid #1E3A5F; padding-bottom: 20px; margin-bottom: 24px; }
                    .logo { font-size: 26px; font-weight: 800; color: #0284C7; letter-spacing: -0.5px; }
                    .logo span { color: #10B981; }
                    h2 { color: #F8FAFC; margin-top: 0; font-size: 20px; }
                    p { font-size: 14px; line-height: 1.6; color: #94A3B8; }
                    .credentials-box { background: #070B14; border: 1px solid #1E3A5F; border-radius: 8px; padding: 16px; margin: 20px 0; }
                    .btn-container { text-align: center; margin: 32px 0; }
                    .btn { display: inline-block; background: #0284C7; color: #FFFFFF !important; text-decoration: none; font-weight: 600; font-size: 15px; padding: 12px 28px; border-radius: 8px; box-shadow: 0 4px 12px rgba(2,132,199,0.3); }
                    .footer { text-align: center; font-size: 12px; color: #64748B; margin-top: 24px; border-top: 1px solid #1E3A5F; padding-top: 16px; }
                    .url-alt { word-break: break-all; font-size: 12px; color: #38BDF8; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="header">
                      <div class="logo">SAED <span>2.0</span></div>
                      <p style="margin: 4px 0 0 0; font-size: 12px; color: #64748B;">Sistema de Administración de Edificios y Copropiedades</p>
                    </div>
                    <h2>Hola %s,</h2>
                    <p>Tu cuenta ha sido creada exitosamente en la plataforma. Para comenzar a utilizar el sistema y garantizar la máxima seguridad, por favor define tu contraseña de acceso personal.</p>
                    <div class="credentials-box">
                      <p style="margin: 0; color: #E2E8F0; font-size: 13px;"><strong>Nombre de usuario:</strong> <span style="color: #38BDF8;">%s</span></p>
                      <p style="margin: 6px 0 0 0; color: #E2E8F0; font-size: 13px;"><strong>Correo electrónico:</strong> %s</p>
                    </div>
                    <div class="btn-container">
                      <a href="%s" class="btn" target="_blank">Configurar Contraseña y Activar</a>
                    </div>
                    <p style="font-size: 12px;">Si el botón no abre automáticamente, copia y pega este enlace en tu navegador web:</p>
                    <p class="url-alt">%s</p>
                    <div class="footer">
                      <p style="margin: 0;">Este enlace de seguridad es de un solo uso y expirará en 48 horas.</p>
                      <p style="margin: 4px 0 0 0;">Si no solicitaste este acceso, por favor desestima este correo.</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(nombre, username, email, enlace, enlace);

            emailService.enviarHtmlPublico(email, asunto, html);
            log.info("Correo de activación enviado exitosamente a {}", email);
        } catch (Exception e) {
            log.error("Fallo al enviar correo de activación a {}: {}", email, e.getMessage());
        }
    }

    private String calcularSha256(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
