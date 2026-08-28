package com.saed.backend.convivencia.service.impl;
import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.repository.MultaRepository;
import com.saed.backend.convivencia.service.MultaService;
import com.saed.backend.common.service.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class MultaServiceImpl implements MultaService {
    private final MultaRepository repo;
    private final EmailService emailService;

    public MultaServiceImpl(MultaRepository repo, EmailService emailService) {
        this.repo = repo;
        this.emailService = emailService;
    }

    
    @Override
    public List<MultaDTO> findAll() {
        return repo.findAll();
    }
    
    @Override
    public MultaDTO findById(Long id) {
        return repo.findById(id);
    }
    
    @Override
    @Transactional
    public void pagar(Long id, String metodo) {
        // En una implementacion real se crearia un pago
        repo.updateEstado(id, "PAGADA");

        // Enviar notificación por email (non-blocking)
        try {
            MultaDTO multa = repo.findById(id);
            if (multa != null) {
                String fecha = multa.getFechaCreacion() != null ? multa.getFechaCreacion().toString() : "";
                emailService.enviarNotificacionMulta(null, multa.getTipo(), multa.getMonto(), fecha);
            }
        } catch (Exception e) {
            System.err.println("Error enviando notificación de multa: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public void anular(Long id) {
        repo.updateEstado(id, "ANULADA");
    }
}
