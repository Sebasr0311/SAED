package com.saed.backend.finanzas.repository;

import com.saed.backend.finanzas.dto.*;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface FinanzasRepository {
    List<ContratoDTO> getContratos();
    Long createContrato(ContratoRequestDTO req, String numContrato);
    void updateEstadoContrato(Long id, String estado);
    List<CuotaDTO> getCuotasPendientes();
    List<CuotaDTO> getCuotasByResidente(Long idResidente);
    Long registrarPago(PagoRequestDTO req, Long idUnidad);
    Long registrarPago(PagoRequestDTO req, Long idUnidad, String estadoInicial, String hash, Long tamanoBytes);
    void actualizarSaldoCuota(Long idCuota, BigDecimal montoAplicado);
    ContratoDetalleDTO getContratoDetalle(Long idContrato);
    void actualizarDocumentoContrato(Long idContrato, String documentoUrl, String documentoHash, long tamanoBytes, String htmlCongelado);
    void generarCuotasIniciales(Long idContrato);

    // GAP-F6-05: Ciclo de vida de aprobación y consulta de pagos
    List<PagoResponseDTO> getPagos(String estado, Long idUnidad, LocalDate fechaDesde, LocalDate fechaHasta, String metodoPago);
    PagoResponseDTO getPagoById(Long idPago);
    void aprobarPago(Long idPago, Long idAprobador);
    void rechazarPago(Long idPago, Long idRechazador, String motivoRechazo);
    void recalcularCarteraUnidad(Long idUnidad);
}
