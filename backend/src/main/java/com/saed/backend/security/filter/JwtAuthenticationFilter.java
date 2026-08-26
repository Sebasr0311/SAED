package com.saed.backend.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, ObjectMapper objectMapper) {
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtProvider.validateToken(jwt)) {
                
                Long userId = jwtProvider.getUserIdFromToken(jwt);
                
                String assignmentHeader = request.getHeader("X-Assignment-Id");
                SaedContext saedContext = null;
                
                if (StringUtils.hasText(assignmentHeader)) {
                    AssignmentService assignmentService = 
                        org.springframework.web.context.support.WebApplicationContextUtils
                        .getRequiredWebApplicationContext(request.getServletContext())
                        .getBean(AssignmentService.class);
                        
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
                            .roleCode(assign.getRol().getCodigo());
                            
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
                    // STATE 1: purely identity context
                    saedContext = SaedContext.builder().userId(userId).build();
                }
                
                // Set into ThreadLocal for Oracle Proxy
                SaedContextHolder.setContext(saedContext);

                // Set into Spring Security for @PreAuthorize
                String role = (saedContext.getRoleCode() != null) ? "ROLE_" + saedContext.getRoleCode() : "ROLE_USER";
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(role))
                );
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            System.err.println("CRITICAL SECURITY ERROR: Cannot set user authentication: " + e.getMessage());
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
