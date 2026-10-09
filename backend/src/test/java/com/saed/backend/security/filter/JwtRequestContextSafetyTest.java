package com.saed.backend.security.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.dto.AuthUserDTO;
import com.saed.backend.identity.repository.AuthRepository;
import com.saed.backend.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtRequestContextSafetyTest {

    private JwtProvider jwtProvider;
    private AssignmentService assignmentService;
    private AuthRepository authRepository;
    private ObjectProvider<AssignmentService> assignmentProvider;
    private ObjectProvider<AuthRepository> authProvider;
    private JwtAuthenticationFilter filter;
    private FilterChain chain;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        clearContexts();
        jwtProvider = new JwtProvider();
        byte[] signingMaterial = new byte[64];
        new SecureRandom().nextBytes(signingMaterial);
        ReflectionTestUtils.setField(jwtProvider, "jwtSecret", Base64.getEncoder().encodeToString(signingMaterial));
        ReflectionTestUtils.setField(jwtProvider, "jwtExpirationMs", 3600000);
        assignmentService = mock(AssignmentService.class);
        authRepository = mock(AuthRepository.class);
        assignmentProvider = mock(ObjectProvider.class);
        authProvider = mock(ObjectProvider.class);
        when(assignmentProvider.getIfAvailable()).thenReturn(assignmentService);
        when(authProvider.getIfAvailable()).thenReturn(authRepository);
        filter = new JwtAuthenticationFilter(jwtProvider, new ObjectMapper(), assignmentProvider, authProvider);
        chain = mock(FilterChain.class);
        request = new MockHttpServletRequest("GET", "/api/v1/synthetic-resource");
        request.addHeader("Authorization", "Bearer " + jwtProvider.generateIdentityToken(20L));
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void cleanUp() {
        clearContexts();
    }

    @Test
    void malformedAssignmentStopsAndClearsBothHolders() throws Exception {
        seedPreviousContexts();
        request.addHeader("X-Assignment-Id", "not-a-number");

        filter.doFilterInternal(request, response, chain);

        assertEquals(400, response.getStatus());
        verifyNoInteractions(chain);
        assertContextsEmpty();
    }

    @Test
    void inactiveOrForeignAssignmentStopsAndClearsBothHolders() throws Exception {
        seedPreviousContexts();
        request.addHeader("X-Assignment-Id", "42");
        when(assignmentService.validateAssignment(42L, 20L)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
        assertContextsEmpty();
    }

    @Test
    void missingExplicitAssignmentDependencyFailsClosed() throws Exception {
        request.addHeader("X-Assignment-Id", "42");
        when(assignmentProvider.getIfAvailable()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
        assertContextsEmpty();
    }

    @Test
    void explicitAssignmentValidationErrorFailsClosedWithoutRawResponse() throws Exception {
        request.addHeader("X-Assignment-Id", "42");
        when(assignmentService.validateAssignment(42L, 20L))
                .thenThrow(new IllegalStateException("synthetic-internal-detail"));

        filter.doFilterInternal(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(!response.getContentAsString().contains("synthetic-internal-detail"));
        verifyNoInteractions(chain);
        assertContextsEmpty();
    }

    @Test
    void authenticationFailureLogsOnlyStableClassification() throws Exception {
        request.addHeader("X-Assignment-Id", "42");
        when(assignmentService.validateAssignment(42L, 20L))
                .thenThrow(new IllegalStateException("synthetic-private-diagnostic"));
        Logger logger = (Logger) LoggerFactory.getLogger(JwtAuthenticationFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.doFilterInternal(request, response, chain);

            assertEquals(1, appender.list.size());
            assertEquals("Authentication context resolution failed (IllegalStateException)",
                    appender.list.get(0).getFormattedMessage());
            assertNull(appender.list.get(0).getThrowableProxy());
            assertTrue(!response.getContentAsString().contains("synthetic-private-diagnostic"));
            verifyNoInteractions(chain);
            assertContextsEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void providerResolutionErrorFailsClosed() throws Exception {
        request.addHeader("X-Assignment-Id", "42");
        when(assignmentProvider.getIfAvailable()).thenThrow(new IllegalStateException("synthetic-provider-detail"));

        filter.doFilterInternal(request, response, chain);

        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
        assertContextsEmpty();
    }

    @Test
    void defaultProfileFailureStopsBeforeChainAndClearsContexts() throws Exception {
        when(authRepository.getUserProfile(20L)).thenThrow(new IllegalStateException("synthetic-profile-detail"));

        filter.doFilterInternal(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(!response.getContentAsString().contains("synthetic-profile-detail"));
        verifyNoInteractions(chain);
        assertContextsEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-42", "9223372036854775808"})
    void invalidAssignmentIdentifiersStopWithoutValidation(String assignmentId) throws Exception {
        request.addHeader("X-Assignment-Id", assignmentId);

        filter.doFilterInternal(request, response, chain);

        assertEquals(400, response.getStatus());
        verifyNoInteractions(chain, assignmentService);
        assertContextsEmpty();
    }

    @Test
    void assignmentWithoutARoleFailsClosed() throws Exception {
        request.addHeader("X-Assignment-Id", "42");
        when(assignmentService.validateAssignment(42L, 20L))
                .thenReturn(Optional.of(new AssignmentResponseDTO()));

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
        assertContextsEmpty();
    }

    @Test
    void responseWriterFailureCannotContinueOrRetainIdentity() throws Exception {
        seedPreviousContexts();
        request.addHeader("X-Assignment-Id", "not-a-number");
        HttpServletResponse brokenResponse = mock(HttpServletResponse.class);
        when(brokenResponse.getWriter()).thenThrow(new IOException("synthetic-writer-failure"));

        assertThrows(IOException.class, () -> filter.doFilterInternal(request, brokenResponse, chain));

        verifyNoInteractions(chain);
        assertContextsEmpty();
    }

    @Test
    void positiveExplicitAssignmentHasExactScopeDuringChainThenClears() throws Exception {
        request.addHeader("X-Assignment-Id", "42");
        when(assignmentService.validateAssignment(42L, 20L)).thenReturn(Optional.of(assignment()));
        doAnswer(invocation -> {
            SaedContext context = SaedContextHolder.getContext();
            assertEquals(20L, context.getUserId());
            assertEquals(10L, context.getOrganizationId());
            assertEquals(11L, context.getPropertyId());
            assertEquals(12L, context.getUnitId());
            assertEquals("RESIDENTE_CONVIVENCIA", context.getRoleCode());
            assertEquals(List.of("ROLE_RESIDENTE_CONVIVENCIA", "SCOPE_RESIDENTE_CONVIVENCIA", "SCOPE_UNIDAD"),
                    SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                            .map(Object::toString).toList());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertContextsEmpty();
    }

    @Test
    void chainFailureStillClearsBothHolders() throws Exception {
        request.addHeader("X-Assignment-Id", "42");
        when(assignmentService.validateAssignment(42L, 20L)).thenReturn(Optional.of(assignment()));
        doThrow(new ServletException("synthetic-chain-failure")).when(chain).doFilter(any(), any());

        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));

        assertContextsEmpty();
    }

    @Test
    void defaultProfilePathPreservesStoredRoleAndScope() throws Exception {
        when(authRepository.getUserProfile(20L)).thenReturn(new AuthUserDTO(
                20L, "synthetic-user", "synthetic@example.invalid", "ADMIN_ORGANIZACION",
                "ORGANIZACION", 10L, null, null));
        doAnswer(invocation -> {
            assertEquals("ADMIN_ORGANIZACION", SaedContextHolder.getContext().getRoleCode());
            assertEquals(10L, SaedContextHolder.getContext().getOrganizationId());
            assertNull(SaedContextHolder.getContext().getUnitId());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertContextsEmpty();
    }

    @Test
    void identityOnlyDiscoveryDoesNotResolveAnAssignment() throws Exception {
        request.setRequestURI("/api/v1/me/contexts");
        request.addHeader("X-Assignment-Id", "not-a-number");
        doAnswer(invocation -> {
            assertEquals(20L, SaedContextHolder.getContext().getUserId());
            assertNull(SaedContextHolder.getContext().getRoleCode());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        verifyNoInteractions(assignmentService, authRepository);
        verify(chain).doFilter(request, response);
        assertContextsEmpty();
    }

    @Test
    void noAssignmentProfileRetainsLegitimateIdentityOnlyBehavior() throws Exception {
        when(authRepository.getUserProfile(20L)).thenReturn(null);
        doAnswer(invocation -> {
            assertEquals(20L, SaedContextHolder.getContext().getUserId());
            assertNull(SaedContextHolder.getContext().getOrganizationId());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertContextsEmpty();
    }

    @Test
    void rejectedThenAnonymousRequestCannotInheritIdentityOnSameThread() throws Exception {
        request.addHeader("X-Assignment-Id", "not-a-number");
        filter.doFilterInternal(request, response, chain);
        verifyNoInteractions(chain);
        MockHttpServletRequest anonymous = new MockHttpServletRequest("OPTIONS", "/api/v1/public-synthetic");
        doAnswer(invocation -> {
            assertNull(SaedContextHolder.getContext());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(anonymous, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertContextsEmpty();
    }

    @Test
    void anonymousEntryDiscardsAStaleCustomContextBeforeChain() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder().userId(99L).organizationId(98L).build());
        MockHttpServletRequest anonymous = new MockHttpServletRequest("GET", "/api/v1/public-synthetic");
        doAnswer(invocation -> {
            assertNull(SaedContextHolder.getContext());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(anonymous, new MockHttpServletResponse(), chain);

        assertContextsEmpty();
    }

    @Test
    void unauthenticatedSpringContextCannotPreserveStaleCustomIdentity() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder().userId(99L).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(99L, null));
        MockHttpServletRequest anonymous = new MockHttpServletRequest("GET", "/api/v1/public-synthetic");
        doAnswer(invocation -> {
            assertNull(SaedContextHolder.getContext());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(anonymous, new MockHttpServletResponse(), chain);

        assertContextsEmpty();
    }

    @Test
    void existingAuthenticatedSpringContextKeepsCompatibilityThenClears() throws Exception {
        request.removeHeader("Authorization");
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(10L)
                .roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                20L, null, List.of(new SimpleGrantedAuthority("SCOPE_ADMIN_ORGANIZACION"))));
        doAnswer(invocation -> {
            assertEquals(20L, SaedContextHolder.getContext().getUserId());
            assertEquals("ADMIN_ORGANIZACION", SaedContextHolder.getContext().getRoleCode());
            assertEquals(20L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertContextsEmpty();
    }

    @Test
    void rejectedThenDifferentUserUsesOnlyTheNewIdentity() throws Exception {
        request.addHeader("X-Assignment-Id", "not-a-number");
        filter.doFilterInternal(request, response, chain);
        MockHttpServletRequest nextRequest = new MockHttpServletRequest("GET", "/api/v1/me/contexts");
        nextRequest.addHeader("Authorization", "Bearer " + jwtProvider.generateIdentityToken(21L));
        doAnswer(invocation -> {
            assertEquals(21L, SaedContextHolder.getContext().getUserId());
            assertNull(SaedContextHolder.getContext().getOrganizationId());
            assertEquals(21L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(nextRequest, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertContextsEmpty();
    }

    private AssignmentResponseDTO assignment() {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setIdAsignacion(42L);
        dto.setRol(new RoleDTO("RESIDENTE_CONVIVENCIA", "UNIDAD"));
        dto.setOrganizacion(new OrganizationDTO(10L, "Synthetic organization"));
        dto.setPropiedad(new PropertyDTO(11L, "Synthetic property"));
        dto.setUnidad(new UnitDTO(12L, "Synthetic unit"));
        return dto;
    }

    private void seedPreviousContexts() {
        SaedContextHolder.setContext(SaedContext.builder().userId(99L).organizationId(98L).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                99L, null, List.of(new SimpleGrantedAuthority("ROLE_SYNTHETIC_PREVIOUS"))));
    }

    private void assertContextsEmpty() {
        assertNull(SaedContextHolder.getContext());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void clearContexts() {
        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }
}
