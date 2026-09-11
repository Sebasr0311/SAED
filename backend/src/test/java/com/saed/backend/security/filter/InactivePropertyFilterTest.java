package com.saed.backend.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.service.PropertyStatusService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InactivePropertyFilterTest {

    @Mock
    private PropertyStatusService propertyStatusService;

    @Mock
    private FilterChain filterChain;

    private InactivePropertyFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        filter = new InactivePropertyFilter(propertyStatusService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void doFilter_getRequest_passesThroughRegardlessOfStatus() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("ADMIN_PROPIEDAD").propertyId(10L).build());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/parqueaderos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(propertyStatusService);
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilter_postWithActiveProperty_passesThrough() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("ADMIN_PROPIEDAD").propertyId(10L).build());
        when(propertyStatusService.isPropertyActive(10L)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/parqueaderos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilter_postWithInactiveProperty_blocksWith403() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("ADMIN_PROPIEDAD").propertyId(10L).build());
        when(propertyStatusService.isPropertyActive(10L)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/parqueaderos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PROPERTY_INACTIVE"));
        assertTrue(response.getContentAsString().contains("inactiva"));
    }

    @Test
    void doFilter_putWithInactiveProperty_blocksWith403() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_PROPIEDAD").propertyId(20L).build());
        when(propertyStatusService.isPropertyActive(20L)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/units/5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PROPERTY_INACTIVE"));
    }

    @Test
    void doFilter_deleteWithInactiveProperty_blocksWith403() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).roleCode("ADMIN_PROPIEDAD").propertyId(30L).build());
        when(propertyStatusService.isPropertyActive(30L)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/gastos/12");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PROPERTY_INACTIVE"));
    }

    @Test
    void doFilter_lifecycleStatusUpdate_exemptFromBlock() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(4L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION")
                .propertyId(40L).build());

        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/properties/40/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(propertyStatusService);
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilter_superadminGlobal_bypassesCheck() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(99L).roleCode("SUPERADMIN").roleScope("GLOBAL")
                .propertyId(50L).build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/parqueaderos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(propertyStatusService);
    }

    @Test
    void doFilter_nullPropertyContext_passesThrough() throws Exception {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(5L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/properties");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(propertyStatusService);
    }
}
