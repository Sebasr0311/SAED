package com.saed.backend.finanzas.service.impl;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.service.FinanzasService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class FinanzasServiceImpl implements FinanzasService {
    private final FinanzasRepository finanzasRepository;
    public FinanzasServiceImpl(FinanzasRepository finanzasRepository) { this.finanzasRepository = finanzasRepository; }

    @Override
    public List<ContratoDTO> getContratos() {
        return finanzasRepository.getContratos();
    }

    @Override
    @Transactional
    public Long createContrato(ContratoRequestDTO request) {
        String numContrato = "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return finanzasRepository.createContrato(request, numContrato);
    }

    @Override
    @Transactional
    public void actualizarEstadoContrato(Long id, String estado) {
        finanzasRepository.updateEstadoContrato(id, estado);
    }

    @Override
    public List<CuotaDTO> getCuotasPendientes() {
        return finanzasRepository.getCuotasPendientes();
    }

    @Override
    @Transactional
    public void registrarPago(PagoRequestDTO request) {
        List<CuotaDTO> pendientes = finanzasRepository.getCuotasPendientes();
        CuotaDTO cuota = pendientes.stream().filter(c -> c.id().equals(request.idCuota())).findFirst()
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada o ya pagada"));
                
        finanzasRepository.registrarPago(request, cuota.idUnidad());
        finanzasRepository.actualizarSaldoCuota(request.idCuota(), request.valorPagado());
    }

    @Override
    public ResidenteDashboardDTO getDashboardResidente(Long idResidente) {
        List<CuotaDTO> cuotas = finanzasRepository.getCuotasByResidente(idResidente);
        return new ResidenteDashboardDTO(idResidente, cuotas);
    }
}
