package com.saed.backend.authorization.service;

import com.saed.backend.audit.AuditService;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyDeletionDTOs;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.repository.TokenActivacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class PropertyDeletionService {

    private static final Logger log = LoggerFactory.getLogger(PropertyDeletionService.class);

    private final PropertyRepository propertyRepository;
    private final PropertyDeletionChallengeService challengeService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final TokenActivacionRepository tokenActivacionRepository;
    private final PropertyStatusService propertyStatusService;

    public PropertyDeletionService(
            PropertyRepository propertyRepository,
            PropertyDeletionChallengeService challengeService,
            EmailService emailService,
            AuditService auditService,
            TokenActivacionRepository tokenActivacionRepository,
            PropertyStatusService propertyStatusService
    ) {
        this.propertyRepository = propertyRepository;
        this.challengeService = challengeService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.tokenActivacionRepository = tokenActivacionRepository;
        this.propertyStatusService = propertyStatusService;
    }

    /**
     * Paso 1-4: Valida permisos de ADMIN_ORGANIZACION, verifica pertenencia de la propiedad,
     * genera un OTP de 6 dígitos seguro y lo despacha al correo registrado del administrador.
     */
    public PropertyDeletionDTOs.RequestResponse requestDeletion(Long propertyId, String ipAddress, String userAgent) {
        SaedContext ctx = SaedContextHolder.getContext();
        validateAdminOrganizacion(ctx);

        Long orgId = ctx.getOrganizationId();
        PropertyDTO property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NoSuchElementException("La propiedad no existe."));

        if (!property.getIdOrganizacion().equals(orgId)) {
            auditService.recordFailure(ctx.getUserId(), orgId, propertyId,
                    "REQUEST_PROPERTY_DELETION", "PROPIEDAD", propertyId,
                    ipAddress, userAgent, property.getEstado(), "CROSS_TENANT_ATTEMPT");
            throw new AccessDeniedException("No tiene permisos para eliminar una propiedad perteneciente a otra organización.");
        }

        // Obtener correo verificado del administrador
        TokenActivacionRepository.UsuarioInfo userInfo = tokenActivacionRepository.obtenerUsuarioInfo(ctx.getUserId())
                .orElseThrow(() -> new IllegalStateException("No se encontró la información del usuario administrador."));

        String email = userInfo.email();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("El usuario no cuenta con un correo electrónico registrado para el envío del código.");
        }

        PropertyDeletionChallengeService.GeneratedChallenge genChallenge = challengeService.createChallenge(
                propertyId, orgId, ctx.getUserId(), email, property.getNombre()
        );

        // Envío de correo
        try {
            emailService.enviarCodigoOtpEliminacion(email, property.getNombre(), genChallenge.rawOtp(), 5);
        } catch (Exception e) {
            log.warn("Fallo al enviar correo de eliminación vía Brevo a {}: {}. [OTP para entorno local: {}]",
                    email, e.getMessage(), genChallenge.rawOtp());
        }

        auditService.recordSuccess(ctx.getUserId(), orgId, propertyId,
                "REQUEST_PROPERTY_DELETION", "PROPIEDAD", propertyId,
                ipAddress, userAgent, property.getEstado(), "CHALLENGE_CREATED");

        return new PropertyDeletionDTOs.RequestResponse(
                true,
                genChallenge.challengeId(),
                "Código de seguridad enviado al correo institucional del administrador.",
                genChallenge.expiresInSeconds(),
                maskEmail(email)
        );
    }

    /**
     * Paso 7: Verifica el OTP provisto por el usuario contra el desafío activo.
     */
    public PropertyDeletionDTOs.VerifyResponse verifyOtp(Long propertyId, PropertyDeletionDTOs.VerifyRequest request, String ipAddress, String userAgent) {
        SaedContext ctx = SaedContextHolder.getContext();
        validateAdminOrganizacion(ctx);

        Long orgId = ctx.getOrganizationId();
        PropertyDTO property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NoSuchElementException("La propiedad no existe."));

        if (!property.getIdOrganizacion().equals(orgId)) {
            throw new AccessDeniedException("No tiene permisos para operar sobre una propiedad de otra organización.");
        }

        boolean verified = challengeService.verifyOtp(
                request.challengeId(), propertyId, orgId, ctx.getUserId(), request.code()
        );

        if (verified) {
            auditService.recordSuccess(ctx.getUserId(), orgId, propertyId,
                    "VERIFY_DELETION_OTP", "PROPIEDAD", propertyId,
                    ipAddress, userAgent, "PENDING", "VERIFIED");

            return new PropertyDeletionDTOs.VerifyResponse(
                    true,
                    true,
                    "Código verificado correctamente. Se requiere confirmación final para proceder con la eliminación definitiva."
            );
        } else {
            auditService.recordFailure(ctx.getUserId(), orgId, propertyId,
                    "VERIFY_DELETION_OTP", "PROPIEDAD", propertyId,
                    ipAddress, userAgent, "PENDING", "INVALID_OTP_CODE");

            return new PropertyDeletionDTOs.VerifyResponse(
                    false,
                    false,
                    "El código de verificación ingresado es incorrecto."
            );
        }
    }

    /**
     * Paso 9: Tras la segunda confirmación, ejecuta la eliminación transaccional física/lógica en Oracle ATP.
     */
    public PropertyDeletionDTOs.ConfirmResponse confirmAndExecuteDeletion(Long propertyId, PropertyDeletionDTOs.ConfirmRequest request, String ipAddress, String userAgent) {
        SaedContext ctx = SaedContextHolder.getContext();
        validateAdminOrganizacion(ctx);

        if (!request.confirmacionDefinitiva()) {
            throw new IllegalArgumentException("Debe confirmar expresamente la eliminación definitiva de la propiedad.");
        }

        Long orgId = ctx.getOrganizationId();
        PropertyDTO property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NoSuchElementException("La propiedad no existe o ya fue eliminada."));

        if (!property.getIdOrganizacion().equals(orgId)) {
            throw new AccessDeniedException("No tiene permisos para eliminar una propiedad de otra organización.");
        }

        // Consume el challenge de forma atómica y valida que esté en estado VERIFIED
        PropertyDeletionChallengeService.DeletionChallenge challenge =
                challengeService.consumeChallengeForFinalExecution(request.challengeId(), propertyId, orgId, ctx.getUserId());

        log.warn("EJECUTANDO ELIMINACIÓN DESTRUCTIVA de propiedad id={}, nombre='{}', orgId={} por usuarioId={}",
                propertyId, property.getNombre(), orgId, ctx.getUserId());

        boolean deleted = propertyRepository.deletePropertyCascade(propertyId, orgId);
        if (!deleted) {
            auditService.recordFailure(ctx.getUserId(), orgId, null,
                    "DELETE", "PROPIEDAD", propertyId,
                    ipAddress, userAgent, property.getEstado(), "DELETE_OPERATION_FAILED");
            throw new IllegalStateException("No se pudo completar la eliminación de la propiedad en la base de datos.");
        }

        // Actualizar cache de estado
        propertyStatusService.updateCache(propertyId, "ELIMINADA");

        auditService.recordSuccess(ctx.getUserId(), orgId, null,
                "DELETE", "PROPIEDAD", propertyId,
                ipAddress, userAgent, property.getEstado(), "DELETED_PERMANENTLY");

        return new PropertyDeletionDTOs.ConfirmResponse(
                true,
                "La propiedad '" + property.getNombre() + "' ha sido eliminada definitivamente del sistema.",
                propertyId
        );
    }

    private void validateAdminOrganizacion(SaedContext ctx) {
        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Usuario no autenticado.");
        }

        String roleCode = ctx.getRoleCode();
        String scope = ctx.getRoleScope();

        // SUPERADMIN no debe operar como admin de organización en este flujo
        if ("SUPERADMIN".equalsIgnoreCase(roleCode) || "GLOBAL".equalsIgnoreCase(scope)) {
            throw new AccessDeniedException("El SUPERADMIN no puede eliminar propiedades desde el contexto operativo de organizaciones.");
        }

        // Exclusivo para ADMIN_ORGANIZACION
        if (!"ADMIN_ORGANIZACION".equalsIgnoreCase(roleCode) && !"ORGANIZACION".equalsIgnoreCase(scope)) {
            throw new AccessDeniedException("Solo el Administrador de la Organización puede eliminar propiedades.");
        }

        if (ctx.getOrganizationId() == null) {
            throw new AccessDeniedException("No se encontró una organización activa en el contexto de seguridad.");
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "correo***@***";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
