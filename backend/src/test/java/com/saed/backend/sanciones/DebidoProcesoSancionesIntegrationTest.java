package com.saed.backend.sanciones;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.sanciones.controller.SancionesController;
import com.saed.backend.sanciones.dto.DescargoRequestDTO;
import com.saed.backend.sanciones.dto.ResolucionRequestDTO;
import com.saed.backend.sanciones.dto.SancionCreateRequestDTO;
import com.saed.backend.sanciones.dto.SancionDTO;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class DebidoProcesoSancionesIntegrationTest {

    @Autowired
    private SancionesController controller;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUnidadId = 1L;
    private Long testPersonaId = 1L;

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
                                new SimpleGrantedAuthority("SCOPE_ADMIN_PROPIEDAD"),
                                new SimpleGrantedAuthority("SCOPE_RESIDENTE")
                        )
                )
        );

        try {
            jdbcTemplate.execute("BEGIN SAED_SEC_MASTER.PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("BEGIN SAED_SEC_MASTER.PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // Obtener una unidad y persona real existentes en la base de datos
        try {
            List<Long> uList = jdbcTemplate.queryForList("SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = 1 AND ROWNUM = 1", Long.class);
            if (!uList.isEmpty()) testUnidadId = uList.get(0);

            List<Long> pList = jdbcTemplate.queryForList("SELECT ID_PERSONA FROM PERSONAS WHERE ROWNUM = 1", Long.class);
            if (!pList.isEmpty()) testPersonaId = pList.get(0);
        } catch (Exception ignored) {}
    }

    @AfterEach
    public void tearDown() {
        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testFlujoCompletoDebidoProceso() {
        // 1. Administrador abre pliego de cargos formal
        SancionCreateRequestDTO createReq = new SancionCreateRequestDTO();
        createReq.setIdUnidad(testUnidadId);
        createReq.setIdPersonaImputada(testPersonaId);
        createReq.setTipoFalta("RUIDO_EXCESIVO_NOCTURNO");
        createReq.setGravedad("LEVE");
        createReq.setDescripcionHechos("Música a alto volumen en horas de la madrugada constatada por vigilante de turno.");
        createReq.setArticuloReglamentoViolado("Artículo 42 Numeral 3");
        createReq.setTipoSancionPropuesta("MULTA_ECONOMICA");
        createReq.setDiasParaDescargos(5);

        ResponseEntity<SancionDTO> respCrear = controller.crearPliego(createReq);
        assertEquals(201, respCrear.getStatusCode().value());
        assertNotNull(respCrear.getBody());

        SancionDTO sancion = respCrear.getBody();
        assertNotNull(sancion.getIdSancion());
        assertTrue(sancion.getNumeroExpediente().startsWith("EXP-"));
        assertEquals("NOTIFICADA", sancion.getEstado());
        assertEquals("LEVE", sancion.getGravedad());
        assertNotNull(sancion.getFechaLimiteDescargos());

        Long sancionId = sancion.getIdSancion();

        // 2. Administrador consulta lista de sanciones
        ResponseEntity<List<SancionDTO>> respList = controller.getAllSanciones();
        assertEquals(200, respList.getStatusCode().value());
        assertTrue(respList.getBody().stream().anyMatch(s -> s.getIdSancion().equals(sancionId)));

        // 3. Residente radica descargos ante el pliego notificado
        DescargoRequestDTO descargoReq = new DescargoRequestDTO();
        descargoReq.setDescargos("Estábamos celebrando un cumpleaños familiar con volumen moderado; lamentamos el inconveniente y nos comprometemos a no repetir.");
        descargoReq.setPruebasAdjuntasUrl("https://storage.saed.com/evidencias/descargo1.pdf");

        ResponseEntity<Map<String, String>> respDescargo = controller.radicarDescargos(sancionId, descargoReq);
        assertEquals(200, respDescargo.getStatusCode().value());

        // Verificar que el estado cambió a EN_DESCARGOS y tiene 1 descargo registrado
        ResponseEntity<SancionDTO> respDetalle = controller.getSancionById(sancionId);
        assertEquals("EN_DESCARGOS", respDetalle.getBody().getEstado());
        assertEquals(1, respDetalle.getBody().getDescargos().size());
        assertEquals(descargoReq.getDescargos(), respDetalle.getBody().getDescargos().get(0).getArgumentosDefensa());

        // 4. Consejo de Administración emite resolución final sancionatoria
        ResolucionRequestDTO resolucionReq = new ResolucionRequestDTO();
        resolucionReq.setDecision("APLICADA");
        resolucionReq.setResolucionFinal("Habiendo valorado los descargos, se ratifica la infracción pero se aplica sanción con atenuante por aceptación.");
        resolucionReq.setMontoMulta(BigDecimal.valueOf(85000.00));

        ResponseEntity<Map<String, String>> respResolucion = controller.emitirResolucion(sancionId, resolucionReq);
        assertEquals(200, respResolucion.getStatusCode().value());

        // 5. Verificar resolución aplicada
        ResponseEntity<SancionDTO> respFinal = controller.getSancionById(sancionId);
        assertEquals("APLICADA", respFinal.getBody().getEstado());
        assertNotNull(respFinal.getBody().getResolucionFinal());
        assertNotNull(respFinal.getBody().getFechaResolucion());

        // 6. Verificar que no se pueden radicar más descargos una vez en firme
        assertThrows(IllegalStateException.class, () -> controller.radicarDescargos(sancionId, descargoReq));
    }

    @Test
    public void testResolucionAbsuelta() {
        SancionCreateRequestDTO createReq = new SancionCreateRequestDTO();
        createReq.setIdUnidad(testUnidadId);
        createReq.setIdPersonaImputada(testPersonaId);
        createReq.setTipoFalta("OBSTRUCCION_ZONA_COMUN");
        createReq.setGravedad("LEVE");
        createReq.setDescripcionHechos("Bicicleta en pasillo comunal.");
        createReq.setTipoSancionPropuesta("AMONESTACION_ESCRITA");

        ResponseEntity<SancionDTO> respCrear = controller.crearPliego(createReq);
        Long sancionId = respCrear.getBody().getIdSancion();

        ResolucionRequestDTO resolucionReq = new ResolucionRequestDTO();
        resolucionReq.setDecision("ABSUELTA");
        resolucionReq.setResolucionFinal("Se comprueba que la bicicleta pertenecía a un visitante temporal y fue retirada inmediatamente.");

        controller.emitirResolucion(sancionId, resolucionReq);

        ResponseEntity<SancionDTO> resp = controller.getSancionById(sancionId);
        assertEquals("ABSUELTA", resp.getBody().getEstado());
    }
}
