package com.saed.backend.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.dto.AuthUserDTO;
import com.saed.backend.identity.repository.AuthRepository;
import com.saed.backend.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtProvider jwtProvider;
    private final org.springframework.beans.factory.ObjectProvider<com.saed.backend.authorization.service.AssignmentService> assignmentServiceProvider;
    private final org.springframework.beans.factory.ObjectProvider<com.saed.backend.identity.repository.AuthRepository> authRepositoryProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, ObjectMapper objectMapper, org.springframework.beans.factory.ObjectProvider<com.saed.backend.authorization.service.AssignmentService> assignmentServiceProvider, org.springframework.beans.factory.ObjectProvider<com.saed.backend.identity.repository.AuthRepository> authRepositoryProvider) {
        this.jwtProvider = jwtProvider;
        this.assignmentServiceProvider = assignmentServiceProvider;
        this.authRepositoryProvider = authRepositoryProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
                        Long userId = null;
            boolean isMockAuth = false;
            
            String jwt = parseJwt(request);
            if (jwt != null && jwtProvider.validateToken(jwt)) {
                userId = jwtProvider.getUserIdFromToken(jwt);
            } else {
                org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                    try {
                        userId = Long.parseLong(auth.getName());
                        isMockAuth = true;
                    } catch (Exception e) {}
                }
            }

            if (userId != null) {

                String assignmentHeader = request.getHeader("X-Assignment-Id");
                SaedContext saedContext = null;

                if (StringUtils.hasText(assignmentHeader)) {
                    AssignmentService assignmentService = assignmentServiceProvider.getIfAvailable();

                    Long assignmentId;
                    try {
                        assignmentId = Long.parseLong(assignmentHeader);
                    } catch (NumberFormatException e) {
                        sendJsonError(response, HttpStatus.BAD_REQUEST, "Invalid X-Assignment-Id format");
                        return;
                    }

                    Optional<AssignmentResponseDTO> optAssign = assignmentService.validateAssignment(assignmentId, userId);
                    if (optAssign.isEmpty()) {
                        sendJsonError(response, HttpStatus.FORBIDDEN, "Invalid or inactive assignment");
                        return;
                    }

                    AssignmentResponseDTO assign = optAssign.get();

                    SaedContext.Builder builder = SaedContext.builder()
                            .userId(userId)
                            .roleCode(assign.getRol().getCodigo())
                            .roleScope(assign.getRol().getAlcance());

                    if (assign.getOrganizacion() != null) {
                        builder.organizationId(assign.getOrganizacion().getId());
                    }
                    if (assign.getPropiedad() != null) {
                        builder.propertyId(assign.getPropiedad().getId());
                    }
                    if (assign.getUnidad() != null) {
                        builder.unitId(assign.getUnidad().getId());
                    }
                    saedContext = builder.build();
                } else {
                    // Sin X-Assignment-Id: resolver la asignacion ACTIVA principal
                    // del usuario via PKG_AUTH_BOOTSTRAP (AUTHID DEFINER + EXEMPT,
                    // salta RLS). Esto da contexto RLS completo al dashboard del
                    // frontend legacy (que no envia el header).
                    AuthRepository authRepository = authRepositoryProvider.getIfAvailable();
                    AuthUserDTO perfil = authRepository.getUserProfile(userId);

                    if (perfil != null && perfil.getRol() != null) {
                        SaedContext.Builder builder = SaedContext.builder()
                                .userId(userId)
                                .roleCode(perfil.getRol())
                                .roleScope(perfil.getAlcance());
                        if (perfil.getIdOrganizacion() != null) {
                            builder.organizationId(perfil.getIdOrganizacion());
                        }
                        if (perfil.getIdPropiedad() != null) {
                            builder.propertyId(perfil.getIdPropiedad());
                        }
                        if (perfil.getIdUnidad() != null) {
                            builder.unitId(perfil.getIdUnidad());
                        }
                        saedContext = builder.build();
                    } else {
                        // STATE 1: purely identity context
                        saedContext = SaedContext.builder().userId(userId).build();
                    }
                }

                // Set into ThreadLocal for Oracle Proxy
                SaedContextHolder.setContext(saedContext);

                // Set into Spring Security for @PreAuthorize:
                // - ROLE_<codigo> por compatibilidad
                // - SCOPE_<codigo> + SCOPE_<ALCANCE> como exigen los controllers 2.0
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (saedContext.getRoleCode() != null) {
                    String code = saedContext.getRoleCode();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + code));
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + code));
                }
                // Scopes derivados por alcance/rol (modelo 2.0)
                if (saedContext.getRoleScope() != null) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + saedContext.getRoleScope()));
                }
                if (saedContext.getRoleCode() != null) {
                    switch (saedContext.getRoleCode()) {
                        case "SUPERADMIN":
                            authorities.add(new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"));
                            authorities.add(new SimpleGrantedAuthority("SCOPE_ADMIN_ORGANIZACION"));
                            authorities.add(new SimpleGrantedAuthority("SCOPE_RESIDENTE"));
                            break;
                        case "ADMIN_ORGANIZACION":
                            authorities.add(new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"));
                            break;
                        default:
                            break;
                    }
                }
                if (authorities.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }

                                if (!isMockAuth) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("CRITICAL SECURITY ERROR: Cannot set user authentication: {}", e.getMessage());
            // Chain continues unauthenticated
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // ALWAYS clean up ThreadLocal (STATE 3 / CLEARING)
            SaedContextHolder.clearContext();
            SecurityContextHolder.clearContext();
        }
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    private void sendJsonError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("success", false);
        errorDetails.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }
}


