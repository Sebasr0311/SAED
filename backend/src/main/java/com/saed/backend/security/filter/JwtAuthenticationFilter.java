package com.saed.backend.security.filter;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtProvider.validateToken(jwt)) {
                
                Long userId = jwtProvider.getUserIdFromToken(jwt);
                
                // Get Context from database based on Header assignment ID
                SaedContext saedContext = null;
                String assignmentHeader = request.getHeader("X-Assignment-Id");
                
                if (StringUtils.hasText(assignmentHeader)) {
                    // Avoid circular dependency in filter, ideally get it from ApplicationContext
                    com.saed.backend.identity.service.ContextService contextService = 
                        org.springframework.web.context.support.WebApplicationContextUtils
                        .getRequiredWebApplicationContext(request.getServletContext())
                        .getBean(com.saed.backend.identity.service.ContextService.class);
                        
                    saedContext = contextService.resolveContext(userId, Long.parseLong(assignmentHeader));
                } else {
                    // Fallback to purely identity context (no tenant)
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
            System.err.println("Cannot set user authentication: " + e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // ALWAYS clean up ThreadLocal
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
}
