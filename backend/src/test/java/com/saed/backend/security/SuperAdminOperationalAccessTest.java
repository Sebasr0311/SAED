package com.saed.backend.security;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3-02 / DEC-02: SUPERADMIN manages platform data only. No operational controller may grant
 * SCOPE_SUPERADMIN at class or method level; platform controllers keep it. Offline reflection
 * check, no Spring context.
 */
class SuperAdminOperationalAccessTest {

    /** Operational (tenant data) controllers: residents, persons, payments, fees, packages, visits,
     * reservations, assemblies, documents, reports, contracts, regulations, automations. */
    private static final List<String> OPERATIONAL = List.of(
            "com.saed.backend.asambleas.controller.AsambleasController",
            "com.saed.backend.asambleas.controller.ActasController",
            "com.saed.backend.comunicacion.controller.WompiController",
            "com.saed.backend.contratos.controller.ContratosPlantillasController",
            "com.saed.backend.contratos.controller.OrgPlantillasContratosController",
            "com.saed.backend.convivencia.controller.BuzonController",
            "com.saed.backend.documentos.controller.DocumentoController",
            "com.saed.backend.automatizaciones.controller.AutomatizacionesController",
            "com.saed.backend.authorization.controller.UnitController",
            "com.saed.backend.authorization.controller.BlockController",
            "com.saed.backend.authorization.controller.PropertyConfigController",
            "com.saed.backend.authorization.controller.AssignmentManagementController",
            "com.saed.backend.dashboard.controller.ReportesController",
            "com.saed.backend.dashboard.controller.ReportesConfiguradosController",
            "com.saed.backend.dashboard.controller.HistorialReportesController",
            "com.saed.backend.dashboard.controller.DashboardController",
            "com.saed.backend.org.controller.OrgDashboardController",
            "com.saed.backend.finanzas.controller.ContratosAdminController",
            "com.saed.backend.finanzas.controller.ContratosController",
            "com.saed.backend.finanzas.controller.PagosController",
            "com.saed.backend.finanzas.controller.PazYSalvoController",
            "com.saed.backend.reservas.controller.ReservasController",
            "com.saed.backend.reglamentos.controller.ReglamentoController",
            "com.saed.backend.person.controller.PersonaController",
            "com.saed.backend.paquetes.controller.PaquetesController",
            "com.saed.backend.porteria.controller.PorteriaController",
            "com.saed.backend.emergencias.controller.EmergenciasController",
            "com.saed.backend.seguros.controller.PolizaSeguroController",
            "com.saed.backend.sanciones.controller.SancionesController",
            "com.saed.backend.consumos.controller.ConsumosController");

    /** Platform controllers that must keep SUPERADMIN access. */
    private static final List<String> PLATFORM = List.of(
            "com.saed.backend.authorization.controller.OrganizationController",
            "com.saed.backend.authorization.controller.PropertyController",
            "com.saed.backend.platform.controller.PlatformPlansController",
            "com.saed.backend.platform.controller.PlatformMembershipsController",
            "com.saed.backend.platform.controller.PlatformAdminsController",
            "com.saed.backend.platform.controller.PlatformDashboardController",
            "com.saed.backend.org.controller.OrgAdminsController");

    @Test
    void operationalControllersDoNotGrantSuperAdmin() throws ClassNotFoundException {
        List<String> grants = new ArrayList<>();
        for (String name : OPERATIONAL) {
            Class<?> controller = Class.forName(name);
            if (grantsSuperAdmin(controller.getAnnotation(PreAuthorize.class))) {
                grants.add(controller.getSimpleName());
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (grantsSuperAdmin(method.getAnnotation(PreAuthorize.class))) {
                    grants.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(grants.isEmpty(), "Operational endpoints must not grant SCOPE_SUPERADMIN (DEC-02): " + grants);
    }

    @Test
    void platformControllersKeepSuperAdmin() throws ClassNotFoundException {
        List<String> missing = new ArrayList<>();
        for (String name : PLATFORM) {
            Class<?> controller = Class.forName(name);
            boolean granted = grantsSuperAdmin(controller.getAnnotation(PreAuthorize.class));
            for (Method method : controller.getDeclaredMethods()) {
                granted |= grantsSuperAdmin(method.getAnnotation(PreAuthorize.class));
            }
            if (!granted) {
                missing.add(controller.getSimpleName());
            }
        }
        assertTrue(missing.isEmpty(), "Platform controllers lost SUPERADMIN access: " + missing);
    }

    private static boolean grantsSuperAdmin(PreAuthorize annotation) {
        return annotation != null && annotation.value().contains("SCOPE_SUPERADMIN");
    }
}
