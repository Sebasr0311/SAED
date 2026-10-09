package com.saed.backend.platform.entitlement;

import com.saed.backend.platform.annotation.RequireModule;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3-07: every controller of a plan module is guarded by @RequireModule so that suspended or
 * expired memberships and plans without the module are restricted consistently. Endpoints that
 * must stay reachable without an entitled tenant (gateway webhook, public certificate
 * verification, payment status used by subscription renewal) are explicitly exempt.
 */
class ModuleGuardCoverageTest {

    private static final Map<String, String> MODULE_CONTROLLERS = Map.ofEntries(
            Map.entry("com.saed.backend.finanzas.controller.CarteraController", "FINANZAS"),
            Map.entry("com.saed.backend.finanzas.controller.ConciliacionController", "FINANZAS"),
            Map.entry("com.saed.backend.finanzas.controller.FlujoCajaController", "FINANZAS"),
            Map.entry("com.saed.backend.finanzas.controller.GastosController", "FINANZAS"),
            Map.entry("com.saed.backend.finanzas.controller.PresupuestoController", "FINANZAS"),
            Map.entry("com.saed.backend.finanzas.controller.PagosController", "FINANZAS"),
            Map.entry("com.saed.backend.finanzas.controller.PazYSalvoController", "FINANZAS"),
            Map.entry("com.saed.backend.finanzas.controller.ResidentesFinanzasController", "FINANZAS"),
            Map.entry("com.saed.backend.comunicacion.controller.WompiController", "FINANZAS"),
            Map.entry("com.saed.backend.paquetes.controller.PaquetesController", "PAQUETES"),
            Map.entry("com.saed.backend.parqueaderos.controller.ParqueaderosController", "PARQUEADEROS"),
            Map.entry("com.saed.backend.reservas.controller.ReservasController", "RESERVAS"),
            Map.entry("com.saed.backend.asambleas.controller.AsambleasController", "ASAMBLEAS"),
            Map.entry("com.saed.backend.asambleas.controller.ActasController", "ASAMBLEAS"),
            Map.entry("com.saed.backend.seguros.controller.PolizaSeguroController", "POLIZAS"),
            Map.entry("com.saed.backend.convivencia.controller.QuejasController", "PQRS"));

    /** Controller#method pairs that must not require a module. */
    private static final Set<String> EXEMPT = Set.of(
            "PagosController#wompiWebhook",
            "PazYSalvoController#verificar",
            "WompiController#getEstadoWompi");

    @Test
    void everyModuleEndpointIsGuardedByItsModule() throws ClassNotFoundException {
        List<String> unguarded = new ArrayList<>();
        for (Map.Entry<String, String> entry : MODULE_CONTROLLERS.entrySet()) {
            Class<?> controller = Class.forName(entry.getKey());
            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                String id = controller.getSimpleName() + "#" + method.getName();
                String module = moduleOf(controller, method);
                if (EXEMPT.contains(id)) {
                    if (module != null) {
                        unguarded.add(id + " must stay reachable without module " + module);
                    }
                } else if (!entry.getValue().equals(module)) {
                    unguarded.add(id + " -> " + module + " (expected " + entry.getValue() + ")");
                }
            }
        }
        assertTrue(unguarded.isEmpty(), "Module guard gaps: " + unguarded);
    }

    @Test
    void exemptEndpointsExist() throws ClassNotFoundException {
        for (String id : EXEMPT) {
            String simple = id.substring(0, id.indexOf('#'));
            String methodName = id.substring(id.indexOf('#') + 1);
            String fqcn = MODULE_CONTROLLERS.keySet().stream().filter(n -> n.endsWith("." + simple)).findFirst()
                    .orElseThrow();
            boolean found = false;
            for (Method m : Class.forName(fqcn).getDeclaredMethods()) {
                found |= m.getName().equals(methodName) && isEndpoint(m);
            }
            assertTrue(found, "exempt endpoint not found: " + id);
        }
    }

    private static boolean isEndpoint(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    private static String moduleOf(Class<?> controller, Method method) {
        RequireModule onMethod = method.getAnnotation(RequireModule.class);
        if (onMethod != null) {
            return onMethod.value();
        }
        RequireModule onClass = controller.getAnnotation(RequireModule.class);
        return onClass == null ? null : onClass.value();
    }
}
