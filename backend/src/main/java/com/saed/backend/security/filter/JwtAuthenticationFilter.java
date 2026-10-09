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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtProvider jwtProvider;
    private final ObjectProvider<AssignmentService> assignmentServiceProvider;
    private final ObjectProvider<AuthRepository> authRepositoryProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, ObjectMapper objectMapper,
                                   ObjectProvider<AssignmentService> assignmentServiceProvider,
                                   ObjectProvider<AuthRepository> authRepositoryProvider) {
        this.jwtProvider = jwtProvider;
        this.assignmentServiceProvider = assignmentServiceProvider;
        this.authRepositoryProvider = authRepositoryProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            try {
                Long userId = null;
                boolean isMockAuth = false;
                Authentication inheritedAuth = SecurityContextHolder.getContext().getAuthentication();
                if (inheritedAuth == null || !inheritedAuth.isAuthenticated()
                        || inheritedAuth instanceof AnonymousAuthenticationToken) {
                    SaedContextHolder.clearContext();
                }

                String jwt = parseJwt(request);
                if (jwt != null && jwtProvider.validateToken(jwt)) {
                    userId = jwtProvider.getUserIdFromToken(jwt);
                    SaedContextHolder.clearContext();
                    SecurityContextHolder.clearContext();
                } else if (inheritedAuth != null && inheritedAuth.isAuthenticated()
                        && !(inheritedAuth instanceof AnonymousAuthenticationToken)) {
                    try {
                        userId = Long.parseLong(inheritedAuth.getName());
                        isMockAuth = true;
                    } catch (NumberFormatException ignored) {
                        if (inheritedAuth.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("SUPERADMIN"))) {
                            userId = 1L;
                            isMockAuth = true;
                        } else if (SaedContextHolder.getContext() != null
                                && SaedContextHolder.getContext().getUserId() != null) {
                            userId = SaedContextHolder.getContext().getUserId();
                            isMockAuth = true;
                        }
                    }
                }

                if (userId != null) {
                    String requestPath = request.getRequestURI();
                    boolean isContextDiscovery = requestPath != null
                            && (requestPath.endsWith("/me/contexts") || requestPath.endsWith("/auth/assignments"));
                    String assignmentHeader = request.getHeader("X-Assignment-Id");
                    SaedContext saedContext;
                    if (isContextDiscovery) {
                        saedContext = SaedContext.builder().userId(userId).build();
                    } else if (StringUtils.hasText(assignmentHeader)) {
                        Long assignmentId;
                        try {
                            assignmentId = Long.parseLong(assignmentHeader);
                        } catch (NumberFormatException e) {
                            sendJsonError(response, HttpStatus.BAD_REQUEST, "Invalid X-Assignment-Id format");
                            return;
                        }
                        if (assignmentId <= 0) {
                            sendJsonError(response, HttpStatus.BAD_REQUEST, "Invalid X-Assignment-Id format");
                            return;
                        }
                        SaedContextHolder.setContext(SaedContext.builder().userId(userId).build());
                        AssignmentService assignmentService = assignmentServiceProvider.getIfAvailable();
                        if (assignmentService == null) {
                            sendJsonError(response, HttpStatus.SERVICE_UNAVAILABLE,
                                    "Authentication context could not be resolved");
                            return;
                        }
                        Optional<AssignmentResponseDTO> optAssign = assignmentService.validateAssignment(assignmentId, userId);
                        if (optAssign.isEmpty()) {
                            sendJsonError(response, HttpStatus.FORBIDDEN, "Invalid or inactive assignment");
                            return;
                        }
                        AssignmentResponseDTO assign = optAssign.get();
                        if (assign.getRol() == null || !StringUtils.hasText(assign.getRol().getCodigo())
                                || !StringUtils.hasText(assign.getRol().getAlcance())) {
                            sendJsonError(response, HttpStatus.FORBIDDEN, "Invalid or inactive assignment");
                            return;
                        }
                        SaedContext.Builder builder = SaedContext.builder().userId(userId)
                                .roleCode(assign.getRol().getCodigo()).roleScope(assign.getRol().getAlcance());
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
                    } else if (isMockAuth && SaedContextHolder.getContext() != null
                            && SaedContextHolder.getContext().getRoleCode() != null) {
                        saedContext = SaedContextHolder.getContext();
                    } else {
                        AuthRepository authRepository = authRepositoryProvider.getIfAvailable();
                        AuthUserDTO perfil = authRepository != null ? authRepository.getUserProfile(userId) : null;
                        if (perfil != null && perfil.getRol() != null) {
                            saedContext = SaedContext.builder().userId(userId).roleCode(perfil.getRol())
                                    .roleScope(perfil.getAlcance()).organizationId(perfil.getIdOrganizacion())
                                    .propertyId(perfil.getIdPropiedad()).unitId(perfil.getIdUnidad()).build();
                        } else {
                            saedContext = SaedContext.builder().userId(userId).build();
                        }
                    }
                    SaedContextHolder.setContext(saedContext);
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (saedContext.getRoleCode() != null) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + saedContext.getRoleCode()));
                        authorities.add(new SimpleGrantedAuthority("SCOPE_" + saedContext.getRoleCode()));
                    }
                    if (saedContext.getRoleScope() != null) {
                        authorities.add(new SimpleGrantedAuthority("SCOPE_" + saedContext.getRoleScope()));
                    }
                    if (authorities.isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    }
                    if (!isMockAuth) {
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(userId, null, authorities));
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Authentication context resolution failed ({})", e.getClass().getSimpleName());
                sendJsonError(response, HttpStatus.SERVICE_UNAVAILABLE, "Authentication context could not be resolved");
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
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
