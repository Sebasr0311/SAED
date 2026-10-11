package com.saed.backend.finanzas;

import com.saed.backend.common.exception.GlobalExceptionHandler;
import com.saed.backend.comunicacion.controller.WompiController;
import com.saed.backend.finanzas.service.WompiService;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F06-04 / F6-21 / F6-22: gateway user routes keep their error types and expose no internal payment columns. */
class WompiControllerErrorMappingTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final WompiService wompi = mock(WompiService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new WompiController(jdbc, wompi, mock(WompiServiceImpl.class)))
            .setControllerAdvice(new GlobalExceptionHandler(mock(NamedParameterJdbcTemplate.class)))
            .build();

    @Test
    void aMissingChargeIsA404NotA500() throws Exception {
        when(wompi.crearIntencion("CUOTA", 55L)).thenThrow(new NoSuchElementException("Cuota no encontrada"));

        mvc.perform(post("/api/v1/pagos/wompi/solicitud").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concepto\":\"CUOTA\",\"idItem\":55}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMissingItemIdIsA400() throws Exception {
        mvc.perform(post("/api/v1/pagos/wompi/solicitud").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concepto\":\"CUOTA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theRegisteredPaymentsListNamesItsColumnsAndHidesInternalOnes() throws Exception {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());

        mvc.perform(get("/api/v1/pagos/registrados")).andExpect(status().isOk());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(SqlParameterSource.class));
        assertFalse(sql.getValue().contains("*"), sql.getValue());
        assertFalse(sql.getValue().contains("IDEMPOTENCY_KEY"), sql.getValue());
        assertFalse(sql.getValue().contains("APROBADO_POR"), sql.getValue());
    }
}
