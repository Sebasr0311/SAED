package com.saed.backend.asambleas;

import com.saed.backend.asambleas.controller.AsambleasController;
import com.saed.backend.asambleas.dto.*;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
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
public class AsambleasIntegrationTest {

    @Autowired
    private AsambleasController controller;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUnidadId = 1L;
    private Long testPersona1Id = 1L;
    private Long testPersona2Id = 2L;

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
                                new SimpleGrantedAuthority("SCOPE_RESIDENTE"),
                                new SimpleGrantedAuthority("SCOPE_PROPIETARIO")
                        )
                )
        );

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Asegurar que existe la unidad 1 y personas 1 y 2
        try {
            List<Map<String, Object>> u = jdbcTemplate.queryForList("SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = 1 AND ROWNUM = 1");
            if (!u.isEmpty()) {
                testUnidadId = ((Number) u.get(0).get("ID_UNIDAD")).longValue();
            }
            List<Map<String, Object>> p = jdbcTemplate.queryForList("SELECT ID_PERSONA FROM PERSONAS ORDER BY ID_PERSONA ASC FETCH FIRST 2 ROWS ONLY");
            if (p.size() >= 2) {
                testPersona1Id = ((Number) p.get(0).get("ID_PERSONA")).longValue();
                testPersona2Id = ((Number) p.get(1).get("ID_PERSONA")).longValue();
            }
        } catch (Exception e) {
            // Se mantienen defaults
        }
    }

    @AfterEach
    public void tearDown() {
        SaedContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testFlujoCompletoAsambleaYVotacion() {
        // 1. Convocar Asamblea Ordinaria
        AsambleaCreateRequestDTO convocarReq = AsambleaCreateRequestDTO.builder()
                .idPropiedad(1L)
                .tipo("ORDINARIA")
                .modalidad("MIXTA")
                .titulo("Asamblea General Ordinaria Test 2026")
                .convocatoriaNumero(1)
                .fechaHoraPrimeraConv("2026-10-20T09:00:00")
                .fechaHoraSegundaConv("2026-10-20T10:00:00")
                .lugarOEnlace("Salón Comunal y Enlace Teams")
                .ordenDelDia("1. Verificación de quórum\n2. Elección de presidente\n3. Aprobación de presupuesto")
                .quorumRequeridoPct(BigDecimal.valueOf(50.01))
                .build();

        ResponseEntity<AsambleaDTO> asambleaRes = controller.convocarAsamblea(convocarReq);
        assertEquals(201, asambleaRes.getStatusCode().value());
        assertNotNull(asambleaRes.getBody());
        Long idAsamblea = asambleaRes.getBody().getIdAsamblea();
        assertNotNull(idAsamblea);
        assertEquals("CONVOCADA", asambleaRes.getBody().getEstado());

        // 2. Registrar Asistencia de Copropietario
        AsistenciaRequestDTO asistReq = AsistenciaRequestDTO.builder()
                .idUnidad(testUnidadId)
                .idPersonaAsistente(testPersona1Id)
                .esPropietarioDirecto("S")
                .coeficientePonderado(BigDecimal.valueOf(52.50))
                .build();

        ResponseEntity<AsistenciaDTO> asistRes = controller.registrarAsistencia(idAsamblea, asistReq);
        assertEquals(201, asistRes.getStatusCode().value());
        assertNotNull(asistRes.getBody());

        // 3. Validar Quórum en tiempo real
        ResponseEntity<QuorumLiveDTO> quorumRes = controller.obtenerQuorumEnVivo(idAsamblea);
        assertEquals(200, quorumRes.getStatusCode().value());
        assertNotNull(quorumRes.getBody());
        assertTrue(quorumRes.getBody().getTieneQuorum(), "Debe alcanzar el quórum requerido");
        assertTrue(quorumRes.getBody().getQuorumAlcanzadoPct().compareTo(BigDecimal.ZERO) > 0);

        // 4. Cambiar estado a EN_CURSO
        ResponseEntity<AsambleaDTO> estadoRes = controller.actualizarEstado(idAsamblea,
                new AsambleaEstadoUpdateRequestDTO("EN_CURSO"));
        assertEquals(200, estadoRes.getStatusCode().value());
        assertEquals("EN_CURSO", estadoRes.getBody().getEstado());

        // 5. Crear Punto de Votación
        VotacionCreateRequestDTO votReq = VotacionCreateRequestDTO.builder()
                .puntoOrdenDia(3)
                .titulo("Aprobación del Presupuesto 2027")
                .descripcion("Votación sobre el incremento del 5% en cuota de administración")
                .tipoMayoriaRequerida("SIMPLE_50_MAS_1")
                .build();

        ResponseEntity<VotacionDTO> votRes = controller.crearPuntoVotacion(idAsamblea, votReq);
        assertEquals(201, votRes.getStatusCode().value());
        assertNotNull(votRes.getBody());
        Long idVotacion = votRes.getBody().getIdVotacion();
        assertEquals("ABIERTA", votRes.getBody().getEstado());

        // 6. Emitir Voto
        VotoRequestDTO votoReq = VotoRequestDTO.builder()
                .idUnidad(testUnidadId)
                .idPersonaVotante(testPersona1Id)
                .opcionVoto("SI")
                .coeficienteVoto(BigDecimal.valueOf(52.50))
                .build();

        ResponseEntity<Void> votoRes = controller.emitirVoto(idAsamblea, idVotacion, votoReq);
        assertEquals(201, votoRes.getStatusCode().value());

        // 7. Cerrar Votación y Verificar Cómputo
        ResponseEntity<VotacionDTO> cierreRes = controller.cerrarVotacion(idAsamblea, idVotacion);
        assertEquals(200, cierreRes.getStatusCode().value());
        assertEquals("CERRADA", cierreRes.getBody().getEstado());
        assertEquals(1, cierreRes.getBody().getTotalVotos());
        assertTrue(cierreRes.getBody().getAprobada(), "El punto debió quedar aprobado");

        // 8. Finalizar Asamblea
        ResponseEntity<AsambleaDTO> finRes = controller.actualizarEstado(idAsamblea,
                new AsambleaEstadoUpdateRequestDTO("FINALIZADA"));
        assertEquals(200, finRes.getStatusCode().value());
        assertEquals("FINALIZADA", finRes.getBody().getEstado());
    }

    @Test
    public void testPoderDeRepresentacionYValidacion() {
        // Convocar asamblea para pruebas de poderes
        AsambleaCreateRequestDTO convocarReq = AsambleaCreateRequestDTO.builder()
                .idPropiedad(1L)
                .tipo("EXTRAORDINARIA")
                .modalidad("VIRTUAL")
                .titulo("Asamblea Extraordinaria Poderes Test")
                .fechaHoraPrimeraConv("2026-11-05T18:00:00")
                .lugarOEnlace("https://meet.saed.com/asamblea-extra")
                .ordenDelDia("1. Reparación Fachada")
                .build();

        ResponseEntity<AsambleaDTO> asambleaRes = controller.convocarAsamblea(convocarReq);
        Long idAsamblea = asambleaRes.getBody().getIdAsamblea();

        // 1. Radicar Poder
        PoderRequestDTO poderReq = PoderRequestDTO.builder()
                .idUnidad(testUnidadId)
                .idPersonaPropietario(testPersona1Id)
                .idPersonaApoderado(testPersona2Id)
                .documentoPoderUrl("https://saed-docs.s3.amazonaws.com/poderes/poder-01.pdf")
                .build();

        ResponseEntity<PoderDTO> poderRes = controller.radicarPoder(idAsamblea, poderReq);
        assertEquals(201, poderRes.getStatusCode().value());
        assertNotNull(poderRes.getBody());
        assertEquals("PENDIENTE_REVISION", poderRes.getBody().getEstado());
        Long idPoder = poderRes.getBody().getIdPoder();

        // 2. Administrador aprueba el poder
        ResponseEntity<PoderDTO> decisionRes = controller.decidirPoder(idAsamblea, idPoder,
                new PoderDecisionDTO("APROBADO"));
        assertEquals(200, decisionRes.getStatusCode().value());
        assertEquals("APROBADO", decisionRes.getBody().getEstado());

        // 3. Verificar en lista de poderes
        ResponseEntity<List<PoderDTO>> listaRes = controller.listarPoderes(idAsamblea);
        assertEquals(200, listaRes.getStatusCode().value());
        assertFalse(listaRes.getBody().isEmpty());
    }
}
