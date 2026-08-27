package com.saed.backend.finanzas.service;

public interface WompiService {
    void procesarWebhook(String payloadRaw) throws Exception;
}
