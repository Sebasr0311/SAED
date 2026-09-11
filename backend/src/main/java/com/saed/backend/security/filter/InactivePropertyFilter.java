package com.saed.backend.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.service.PropertyStatusService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * InactivePropertyFilter — Enforces freeze semantics on inactive properties.
 *
 * If a property is INACTIVA, operational users (ADMIN_PROPIEDAD, PORTERO, RESIDENTE)
 * cannot mutate records (POST, PUT, PATCH, DELETE).
 * Safe HTTP methods (GET, HEAD, OPTIONS) remain accessible for consulting historical data.
 * Administrative lifecycle endpoints and SUPERADMIN are exempt.
 */
@Component
public class InactivePropertyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InactivePropertyFilter.class);

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final org.springframework.beans.factory.ObjectProvider<PropertyStatusService> propertyStatusServiceProvider;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public InactivePropertyFilter(org.springframework.beans.factory.ObjectProvider<PropertyStatusService> propertyStatusServiceProvider,
                                  ObjectMapper objectMapper) {
        this.propertyStatusServiceProvider = propertyStatusServiceProvider;
        this.objectMapper = objectMapper;
    }

    public InactivePropertyFilter(PropertyStatusService propertyStatusService, ObjectMapper objectMapper) {
        this.propertyStatusServiceProvider = new org.springframework.beans.factory.support.StaticListableBeanFactory(
                java.util.Map.of("propertyStatusService", propertyStatusService)).getBeanProvider(PropertyStatusService.class);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        if (!MUTATING_METHODS.contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Global superadmin bypass
        if ("SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode()) || "GLOBAL".equalsIgnoreCase(ctx.getRoleScope())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        // Exempt management / lifecycle endpoints:
        // 1. Status update endpoint (allows org admins to reactivate)
        if (uri.matches("^/api/v1/properties/\\d+/status$")) {
            filterChain.doFilter(request, response);
            return;
        }
        // 2. Organization, Platform and SuperAdmin management endpoints
        if (uri.startsWith("/api/v1/org/") || uri.startsWith("/api/v1/platform/") || uri.startsWith("/api/v1/superadmin/")) {
            filterChain.doFilter(request, response);
            return;
        }
        // 3. Auth, health and ping
        if (uri.startsWith("/api/v1/auth/") || uri.startsWith("/api/v1/health") || uri.startsWith("/api/v1/ping")) {
            filterChain.doFilter(request, response);
            return;
        }

        PropertyStatusService propertyStatusService = propertyStatusServiceProvider.getIfAvailable();
        if (propertyStatusService == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long propertyId = ctx.getPropertyId();
        if (!propertyStatusService.isPropertyActive(propertyId)) {
            log.warn("BLOCKED MUTATION: Property {} is INACTIVA. Method: {}, URI: {}, User: {}",
                    propertyId, method, uri, ctx.getUserId());

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> errorBody = Map.of(
                    "success", false,
                    "status", HttpStatus.FORBIDDEN.value(),
                    "code", "PROPERTY_INACTIVE",
                    "message", "La copropiedad se encuentra inactiva. Las operaciones de modificación están temporalmente deshabilitadas."
            );

            objectMapper.writeValue(response.getOutputStream(), errorBody);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
