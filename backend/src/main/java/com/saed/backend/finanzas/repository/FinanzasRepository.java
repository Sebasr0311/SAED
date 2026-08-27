package com.saed.backend.finanzas.repository;
import com.saed.backend.finanzas.dto.*;
import java.util.List;
import java.math.BigDecimal;
public interface FinanzasRepository {
    List<ContratoDTO> getContratos();
    Long createContrato(ContratoRequestDTO req, String numContrato);
    void updateEstadoContrato(Long id, String estado);
    List<CuotaDTO> getCuotasPendientes();
    List<CuotaDTO> getCuotasByResidente(Long idResidente);
    Long registrarPago(PagoRequestDTO req, Long idUnidad);
    void actualizarSaldoCuota(Long idCuota, BigDecimal montoAplicado);
}
