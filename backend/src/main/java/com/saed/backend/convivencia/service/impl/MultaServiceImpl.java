package com.saed.backend.convivencia.service.impl;
import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.repository.MultaRepository;
import com.saed.backend.convivencia.service.MultaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class MultaServiceImpl implements MultaService {
    private final MultaRepository repo;

    public MultaServiceImpl(MultaRepository repo) {
        this.repo = repo;
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
    }
    
    @Override
    @Transactional
    public void anular(Long id) {
        repo.updateEstado(id, "ANULADA");
    }
}
