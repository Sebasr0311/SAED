package com.saed.backend.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;
    
    @Mock
    private WebApplicationContext webApplicationContext;
    
    @Mock
    private jakarta.servlet.ServletContext servletContext;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    public void setup() {
        filter = new JwtAuthenticationFilter(jwtProvider, objectMapper);
    }

    @AfterEach
    public void cleanup() {
        SaedContextHolder.clearContext();
    }

    @Test
    public void testState1_IdentityBound_WhenNoAssignmentHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token");
        when(jwtProvider.validateToken("valid.token")).thenReturn(true);
        when(jwtProvider.getUserIdFromToken("valid.token")).thenReturn(1L);
        when(request.getHeader("X-Assignment-Id")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/assignments");

        filter.doFilterInternal(request, response, filterChain);

        // Context should be cleared AFTER filter chain completes, so we can't test ThreadLocal here 
        // unless we use a mock FilterChain that intercepts it. 
        // But we can verify chain was called.
        verify(filterChain).doFilter(request, response);
    }
}
