package com.saed.backend.finanzas.service;

import java.util.Map;

public interface WompiService {
    void procesarWebhook(String payloadRaw) throws Exception;
    Map<String, Object> crearIntencion(String concepto, Long idItem) throws Exception;
}
