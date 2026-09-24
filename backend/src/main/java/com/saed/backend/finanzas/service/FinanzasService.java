package com.saed.backend.finanzas.service;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.finanzas.dto.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.List;

public interface FinanzasService {
    List<ContratoDTO> getContratos();
    ContratoDetalleDTO getContratoDetalle(Long idContrato);
    Long createContrato(ContratoRequestDTO request);
    void actualizarEstadoContrato(Long id, String estado);
    Resource descargarPdfContrato(Long idContrato);
    List<CuotaDTO> getCuotasPendientes();
    void registrarPago(PagoRequestDTO request);
    ResidenteDashboardDTO getDashboardResidente(Long idResidente);

    // GAP-F6-05: Ciclo de vida y gestión de pagos manuales
    Long registrarPagoManual(PagoRequestDTO request, MultipartFile comprobante);
    List<PagoResponseDTO> getPagos(String estado, Long idUnidad, LocalDate fechaDesde, LocalDate fechaHasta, String metodoPago);
    PagoResponseDTO getPagoDetalle(Long idPago);
    PagoResponseDTO aprobarPago(Long idPago);
    PagoResponseDTO rechazarPago(Long idPago, String motivoRechazo);
    FileStorageService.StoredFile subirComprobante(MultipartFile file);
    Resource descargarComprobante(Long idPago);
}
