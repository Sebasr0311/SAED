package com.saed.backend.comunicacion;

import com.saed.backend.comunicacion.controller.AlertasController;
import com.saed.backend.comunicacion.controller.ComunicadosController;
import com.saed.backend.comunicacion.dto.AlertaDTO;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.convivencia.controller.BuzonController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CentroComunicacionesTest {

    @Autowired
    private AlertasController alertasController;

    @Autowired
    private ComunicadosController comunicadosController;

    @Autowired
    private BuzonController buzonController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setup() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin_global",
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("SCOPE_SUPERADMIN"),
                                new SimpleGrantedAuthority("SCOPE_ADMIN_ORGANIZACION"),
                                new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD")
                        )
                )
        );

        try {
            jdbcTemplate.execute("BEGIN SAED_SEC_MASTER.PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN SAED_SEC_MASTER.PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    @AfterEach
    public void teardown() {
        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testCrearAlertaOperativaYMarcarAtendida() {
        Map<String, Object> payload = Map.of(
                "tipoAlerta", "MORA_CUOTA",
                "numeroApartamento", "302",
                "nombreResidente", "Alejandro Morales",
                "estadoCuota", "EN_MORA",
                "idPropiedad", 1L
        );

        ResponseEntity<AlertaDTO> resp = alertasController.crearAlerta(payload);
        assertNotNull(resp);
        assertEquals(201, resp.getStatusCode().value());

        List<AlertaDTO> lista = alertasController.getAlertas(null, 1L);
        assertNotNull(lista);
        assertFalse(lista.isEmpty());

        AlertaDTO creada = lista.stream()
                .filter(a -> "302".equals(a.getNumeroApartamento()) && "MORA_CUOTA".equals(a.getTipoAlerta()))
                .findFirst()
                .orElse(null);

        assertNotNull(creada, "La alerta operativa creada debe existir en el listado");
        assertEquals("N", creada.getLeida());

        // Marcar como atendida / leída
        ResponseEntity<Void> readResp = alertasController.marcarAtendida(creada.getIdAlerta());
        assertEquals(204, readResp.getStatusCode().value());

        // Verificar que ahora está leída
        List<AlertaDTO> soloNoLeidas = alertasController.getAlertas("true", 1L);
        boolean siguePendiente = soloNoLeidas.stream().anyMatch(a -> a.getIdAlerta().equals(creada.getIdAlerta()));
        assertFalse(siguePendiente, "La alerta atendida ya no debe figurar como pendiente");
    }

    @Test
    public void testMarcarTodasAlertasLeidas() {
        alertasController.crearAlerta(Map.of("tipoAlerta", "INSPECCION", "numeroApartamento", "401", "idPropiedad", 1L));
        alertasController.crearAlerta(Map.of("tipoAlerta", "RUIDO", "numeroApartamento", "402", "idPropiedad", 1L));

        ResponseEntity<Void> resp = alertasController.marcarTodasLeidas(1L);
        assertEquals(204, resp.getStatusCode().value());

        List<AlertaDTO> pendientes = alertasController.getAlertas("true", 1L);
        assertTrue(pendientes.isEmpty(), "No deben quedar alertas pendientes tras marcar todas");
    }

    @Test
    public void testPublicarYArchivarAvisoOficial() {
        String titulo = "Corte programado de agua por mantenimiento " + System.currentTimeMillis();
        Map<String, Object> payload = Map.of(
                "titulo", titulo,
                "mensaje", "Se suspenderá el servicio entre 8:00 AM y 12:00 PM.",
                "prioridad", "ALTA",
                "tipoSegmentacion", "TODOS",
                "idPropiedad", 1L,
                "enviarEmail", false
        );

        ResponseEntity<Map<String, Object>> resp = comunicadosController.postAviso(payload);
        assertEquals(200, resp.getStatusCode().value());

        List<Map<String, Object>> avisos = comunicadosController.getAvisos(1L);
        assertNotNull(avisos);

        Map<String, Object> avisoPublicado = avisos.stream()
                .filter(a -> titulo.equals(a.get("TITULO")))
                .findFirst()
                .orElse(null);

        assertNotNull(avisoPublicado, "El aviso publicado debe existir en la lista activa");
        assertEquals("IMPORTANTE", String.valueOf(avisoPublicado.get("PRIORIDAD")));

        Long idAviso = ((Number) avisoPublicado.get("ID_COMUNICADO")).longValue();

        // Archivar aviso
        ResponseEntity<Void> archivarResp = comunicadosController.archivarAviso(idAviso);
        assertEquals(204, archivarResp.getStatusCode().value());

        // Verificar que ya no figura en la lista activa
        List<Map<String, Object>> avisosActualizados = comunicadosController.getAvisos(1L);
        boolean sigueActivo = avisosActualizados.stream().anyMatch(a -> idAviso.equals(((Number) a.get("ID_COMUNICADO")).longValue()));
        assertFalse(sigueActivo, "El aviso archivado no debe aparecer en avisos activos");
    }

    @Test
    public void testBuzonNotificacionesRolAdmin() {
        ResponseEntity<?> buzon = buzonController.getMyBuzon(null);
        assertNotNull(buzon);
        assertEquals(200, buzon.getStatusCode().value());
        assertTrue(buzon.getBody() instanceof List);
    }
}
