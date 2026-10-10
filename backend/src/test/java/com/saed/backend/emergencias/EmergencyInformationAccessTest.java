package com.saed.backend.emergencias;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.emergencias.controller.EmergenciasController;
import com.saed.backend.emergencias.repository.ContactoEmergenciaRepository;
import com.saed.backend.emergencias.repository.PlanEmergenciaRepository;
import com.saed.backend.emergencias.service.impl.EmergenciasServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Emergency information reaches every profile that lives in or guards the property (plan F10-04,
 * audit F10-20) and nobody else; missing records are reported as not found.
 */
class EmergencyInformationAccessTest {

    private static final List<String> INTENDED_READERS = List.of(
            "SCOPE_ADMIN_PROPIEDAD", "SCOPE_ADMIN_ORGANIZACION", "SCOPE_PORTERO",
            "SCOPE_RESIDENTE", "SCOPE_PROPIETARIO", "SCOPE_PROPIETARIO_UNIDAD", "SCOPE_RESIDENTE_CONVIVENCIA");

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void everyReadEndpointIsOpenToAllIntendedProfilesAndClosedToSuperadmin() {
        int readEndpoints = 0;
        for (Method method : EmergenciasController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(GetMapping.class)) {
                continue;
            }
            readEndpoints++;
            PreAuthorize rule = method.getAnnotation(PreAuthorize.class);
            assertNotNull(rule, method.getName());
            for (String scope : INTENDED_READERS) {
                assertTrue(rule.value().contains("'" + scope + "'"), method.getName() + " must allow " + scope);
            }
            assertFalse(rule.value().contains("SUPERADMIN"), method.getName() + " must not allow SUPERADMIN (DEC-02)");
        }
        assertTrue(readEndpoints >= 5);
    }

    @Test
    void writeEndpointsStayRestrictedToThePropertyAdministrator() {
        for (Method method : EmergenciasController.class.getDeclaredMethods()) {
            PreAuthorize rule = method.getAnnotation(PreAuthorize.class);
            if (rule == null || method.isAnnotationPresent(GetMapping.class)) {
                continue;
            }
            assertTrue(rule.value().equals("hasAuthority('SCOPE_ADMIN_PROPIEDAD')"), method.getName());
        }
    }

    @Test
    void missingContactOrPlanIsNotFound() {
        PlanEmergenciaRepository plans = mock(PlanEmergenciaRepository.class);
        ContactoEmergenciaRepository contacts = mock(ContactoEmergenciaRepository.class);
        when(plans.findByIdAndPropiedad(any(), any())).thenReturn(Optional.empty());
        when(contacts.findByIdAndPropiedad(any(), any())).thenReturn(Optional.empty());
        EmergenciasServiceImpl service = new EmergenciasServiceImpl(plans, contacts);
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(2L).propertyId(3L)
                .roleCode("RESIDENTE").build());

        assertThrows(NoSuchElementException.class, () -> service.getContactoById(99L));
        assertThrows(NoSuchElementException.class, () -> service.getPlanById(99L));
        assertThrows(NoSuchElementException.class, () -> service.deleteContacto(99L));
    }
}
