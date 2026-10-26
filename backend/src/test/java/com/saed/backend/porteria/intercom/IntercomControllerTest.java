package com.saed.backend.porteria.intercom;

import com.saed.backend.comunicacion.controller.ComunicadosController;
import com.saed.backend.porteria.controller.PorteriaController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IntercomControllerTest {

    private final IntercomService service = mock(IntercomService.class);
    private final IntercomController controller = new IntercomController(service);

    @Test
    void residentDecisionIsForwardedFromTheDashboardPayload() {
        controller.confirmar(Map.of("idMensaje", 9, "confirmado", 1));
        controller.confirmar(Map.of("idMensaje", "10", "confirmado", 0));
        verify(service).decide(9L, true);
        verify(service).decide(10L, false);
    }

    @Test
    void decisionWithoutAnswerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> controller.confirmar(Map.of("idMensaje", 9)));
        assertThrows(IllegalArgumentException.class, () -> controller.confirmar(Map.of("confirmado", 1)));
    }

    @Test
    void gateEndpointsDelegateToPersistedState() {
        controller.notificar(Map.of("codigoQr", "tok"));
        controller.resultado(5000L);
        controller.cancelar(9L);
        verify(service).requestFromQr("tok");
        verify(service).resultForVisit(5000L);
        verify(service).cancel(9L);
    }

    @Test
    void noControllerKeepsTheFixedConfirmationStubs() {
        assertFalse(maps(ComunicadosController.class, "/resultado-notificar"));
        assertFalse(maps(ComunicadosController.class, "/confirmar"));
        assertFalse(maps(ComunicadosController.class, "/confirmar-pendiente"));
        assertFalse(maps(PorteriaController.class, "/qr/notificar"));
    }

    private static boolean maps(Class<?> type, String path) {
        for (Method m : type.getDeclaredMethods()) {
            GetMapping g = m.getAnnotation(GetMapping.class);
            PostMapping p = m.getAnnotation(PostMapping.class);
            String[] values = g != null ? g.value() : (p != null ? p.value() : new String[0]);
            if (Arrays.asList(values).contains(path)) {
                return true;
            }
        }
        return false;
    }
}
