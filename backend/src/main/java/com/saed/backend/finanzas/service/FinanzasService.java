package com.saed.backend.finanzas.service;
import com.saed.backend.finanzas.dto.*;
import java.util.List;
public interface FinanzasService {
    List<ContratoDTO> getContratos();
    Long createContrato(ContratoRequestDTO request);
    void actualizarEstadoContrato(Long id, String estado);
    List<CuotaDTO> getCuotasPendientes();
    void registrarPago(PagoRequestDTO request);
    ResidenteDashboardDTO getDashboardResidente(Long idResidente);
}
