package com.saed.backend.paquetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.convivencia.controller.BuzonController;
import com.saed.backend.convivencia.service.NotificacionService;
import com.saed.backend.paquetes.controller.PaquetesController;
import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.repository.PaquetesRepository;
import com.saed.backend.paquetes.service.impl.PaquetesServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C-11 / F8-01: the package pickup PIN must only reach the recipient resident.
 * Staff (portero / admin) never receive it and delivery verification is server-side.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PackagePinExposureTest {

    private static final Long PROPERTY_ID = 3L;
    private static final Long UNIT_ID = 101L;
    private static final Long OTHER_UNIT_ID = 202L;
    private static final Long PACKAGE_ID = 55L;
    private static final String PIN = "4821";

    @Mock private PaquetesRepository paquetesRepository;
    @Mock private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditService auditService;
    @Mock private NotificacionService notificacionService;

    private PaquetesServiceImpl service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new PaquetesServiceImpl(paquetesRepository, jdbcTemplate, fileStorageService, auditService);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PaquetesController(service), new BuzonController(notificacionService, service)).build();

        PaqueteDTO stored = paquete("RECIBIDO");
        when(paquetesRepository.getPaquetesList()).thenReturn(List.of(stored));
        when(paquetesRepository.getPaquetesByUnidad(UNIT_ID)).thenReturn(List.of(stored));
        when(paquetesRepository.getPaqueteById(PACKAGE_ID)).thenReturn(Optional.of(stored));
        when(paquetesRepository.registrarPaquete(any(), anyLong(), anyString(), anyLong())).thenReturn(stored);
        when(paquetesRepository.actualizarPaquete(eq(PACKAGE_ID), any())).thenReturn(stored);
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static PaqueteDTO paquete(String estado) {
        return new PaqueteDTO(PACKAGE_ID, PROPERTY_ID, 1L, UNIT_ID, "101", 900L, "Laura Gomez",
                "Servientrega", "GUIA-1", "Caja", "MEDIANO", null, PIN,
                ZonedDateTime.parse("2026-10-01T10:00:00-05:00"), "Portero 1",
                null, null, null, null, null, estado);
    }

    private static void asPortero() {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("PORTERO")
                .roleScope("PROPIEDAD").propertyId(PROPERTY_ID).build());
    }

    private static void asAdminPropiedad() {
        SaedContextHolder.setContext(SaedContext.builder().userId(8L).roleCode("ADMIN_PROPIEDAD")
                .roleScope("PROPIEDAD").propertyId(PROPERTY_ID).build());
    }

    private static void asResident(Long unitId) {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).roleCode("RESIDENTE")
                .roleScope("UNIDAD").propertyId(PROPERTY_ID).unitId(unitId).build());
    }

    // ---- Staff never receive the PIN ----------------------------------------------------

    @Test
    void portero_packageListJson_hasNoPinField() throws Exception {
        asPortero();
        mockMvc.perform(get("/api/v1/paquetes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idPaquete").value(PACKAGE_ID))
                .andExpect(jsonPath("$[0].codigoRetiroPin").doesNotExist())
                .andExpect(content().string(not(containsString(PIN))));
    }

    @Test
    void admin_packageDetailJson_hasNoPinField() throws Exception {
        asAdminPropiedad();
        mockMvc.perform(get("/api/v1/paquetes/" + PACKAGE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPaquete").value(PACKAGE_ID))
                .andExpect(jsonPath("$.codigoRetiroPin").doesNotExist())
                .andExpect(content().string(not(containsString(PIN))));
    }

    @Test
    void portero_buzonPackageEndpoints_haveNoPinField() throws Exception {
        asPortero();
        mockMvc.perform(get("/api/v1/buzon/paquetes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoRetiroPin").doesNotExist());
        mockMvc.perform(get("/api/v1/buzon/paquetes-pendientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].codigoRetiroPin").doesNotExist());
        mockMvc.perform(get("/api/v1/buzon").param("idApartamento", String.valueOf(UNIT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoRetiroPin").doesNotExist())
                .andExpect(content().string(not(containsString(PIN))));
    }

    @Test
    void portero_registerUpdateAndDeliverResponses_haveNoPin() {
        asPortero();
        PaqueteRequestDTO request = new PaqueteRequestDTO(UNIT_ID, null, "Servientrega", "GUIA-1",
                "Caja", "MEDIANO", null, 1L);

        assertNull(service.registrarPaquete(request).codigoRetiroPin());
        assertNull(service.actualizarPaquete(PACKAGE_ID, request).codigoRetiroPin());

        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        when(paquetesRepository.registrarEntrega(eq(PACKAGE_ID), any(), anyLong())).thenReturn(true);
        assertNull(service.registrarEntrega(PACKAGE_ID, new PaqueteEntregaDTO(PIN, 900L, 1L, null))
                .codigoRetiroPin());
    }

    @Test
    void redactedDto_serializesWithoutPinKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(paquete("RECIBIDO").withoutPin());
        assertFalse(json.contains("codigoRetiroPin"), json);
        assertFalse(json.contains(PIN), json);
    }

    // ---- Recipient resident keeps access to their own PIN --------------------------------

    @Test
    void recipientResident_seesOwnPin_inListAndDetail() throws Exception {
        asResident(UNIT_ID);
        mockMvc.perform(get("/api/v1/paquetes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoRetiroPin").value(PIN));
        assertEquals(PIN, service.getPaqueteById(PACKAGE_ID).codigoRetiroPin());
    }

    @Test
    void otherUnitResident_cannotObtainThePin() {
        asResident(OTHER_UNIT_ID);
        assertThrows(AccessDeniedException.class, () -> service.getPaqueteById(PACKAGE_ID));
        assertThrows(AccessDeniedException.class, () -> service.getPaquetesByUnidad(UNIT_ID));
    }

    @Test
    void missingContext_neverExposesThePin() {
        SaedContextHolder.clearContext();
        assertNull(service.getPaqueteById(PACKAGE_ID).codigoRetiroPin());
    }

    // ---- Delivery verification is server-side ---------------------------------------------

    @Test
    void delivery_withWrongPin_isRejectedServerSide() {
        asPortero();
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> service.registrarEntrega(PACKAGE_ID, new PaqueteEntregaDTO("0000", 900L, 1L, null)));

        verify(paquetesRepository).incrementarIntentosFallidos(PACKAGE_ID);
        verify(paquetesRepository, never()).registrarEntrega(anyLong(), any(), anyLong());
    }

    @Test
    void delivery_withCorrectPin_isAccepted() {
        asPortero();
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        when(paquetesRepository.registrarEntrega(eq(PACKAGE_ID), any(), anyLong())).thenReturn(true);

        service.registrarEntrega(PACKAGE_ID, new PaqueteEntregaDTO(" " + PIN + " ", 900L, 1L, null));

        verify(paquetesRepository).registrarEntrega(eq(PACKAGE_ID), any(), eq(7L));
        verify(paquetesRepository, never()).incrementarIntentosFallidos(anyLong());
    }
}
