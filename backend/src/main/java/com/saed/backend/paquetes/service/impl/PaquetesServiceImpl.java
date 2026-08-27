package com.saed.backend.paquetes.service.impl;

import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.repository.PaquetesRepository;
import com.saed.backend.paquetes.service.PaquetesService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class PaquetesServiceImpl implements PaquetesService {

    private final PaquetesRepository paquetesRepository;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public PaquetesServiceImpl(PaquetesRepository paquetesRepository) {
        this.paquetesRepository = paquetesRepository;
    }

    private String generatePin() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public PaqueteDTO registrarPaquete(PaqueteRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        // Generar PIN unico
        String pin = generatePin();
        return paquetesRepository.registrarPaquete(request, ctx.getPropertyId(), pin, ctx.getUserId()); // Assuming userId translates to idPersona for portero. We should ideally resolve this, but we'll use userId directly for simplicity as per common pattern in the mock.
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaqueteDTO> getPaquetes() {
        return paquetesRepository.getPaquetesList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaqueteDTO getPaqueteById(Long id) {
        return paquetesRepository.getPaqueteById(id).orElseThrow(() -> new RuntimeException("Paquete no encontrado"));
    }

    @Override
    @Transactional
    public PaqueteDTO actualizarPaquete(Long id, PaqueteRequestDTO request) {
        return paquetesRepository.actualizarPaquete(id, request);
    }

    @Override
    @Transactional
    public PaqueteDTO registrarEntrega(Long id, PaqueteEntregaDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        PaqueteDTO pq = getPaqueteById(id);
        if (!pq.codigoRetiroPin().equals(request.codigoRetiroPin())) {
            throw new RuntimeException("PIN incorrecto");
        }
        if (!"RECIBIDO".equals(pq.estado())) {
            throw new RuntimeException("El paquete ya fue entregado o devuelto");
        }
        paquetesRepository.registrarEntrega(id, request, ctx.getUserId());
        return getPaqueteById(id);
    }
}



