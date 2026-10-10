package com.saed.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** DEC-02 for the F9 operational controllers: none grants SCOPE_SUPERADMIN at class or method level. */
class F9OperationsSuperAdminAccessTest {

    private static final List<String> F9_CONTROLLERS = List.of(
            "com.saed.backend.activos.controller.ActivoController",
            "com.saed.backend.mantenimiento.controller.MantenimientoController",
            "com.saed.backend.mantenimiento.controller.PlanMantenimientoController",
            "com.saed.backend.obras.controller.ObraController",
            "com.saed.backend.incidentes.controller.IncidenteController",
            "com.saed.backend.pqrs.controller.TicketController",
            "com.saed.backend.proveedores.controller.ProveedorController",
            "com.saed.backend.trabajadores.controller.TrabajadorController");

    @Test
    void f9ControllersDoNotGrantSuperAdmin() throws ClassNotFoundException {
        List<String> grants = new ArrayList<>();
        for (String name : F9_CONTROLLERS) {
            Class<?> controller = Class.forName(name);
            if (grants(controller.getAnnotation(PreAuthorize.class))) {
                grants.add(controller.getSimpleName());
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (grants(method.getAnnotation(PreAuthorize.class))) {
                    grants.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(grants.isEmpty(), "F9 endpoints must not grant SCOPE_SUPERADMIN (DEC-02): " + grants);
    }

    private static boolean grants(PreAuthorize annotation) {
        return annotation != null && annotation.value().contains("SCOPE_SUPERADMIN");
    }
}
