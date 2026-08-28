package com.saed.backend.finanzas.service.impl;

import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.service.TemplateRenderService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;

@Service
public class FinanzasServiceImpl implements FinanzasService {
    private final FinanzasRepository finanzasRepository;
    private final PdfService pdfService;
    private final EmailService emailService;
    private final TemplateRenderService templateService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanzasServiceImpl(FinanzasRepository finanzasRepository, PdfService pdfService, EmailService emailService, TemplateRenderService templateService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.finanzasRepository = finanzasRepository;
        this.pdfService = pdfService;
        this.emailService = emailService;
        this.templateService = templateService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ContratoDTO> getContratos() {
        return finanzasRepository.getContratos();
    }

    @Override
    @Transactional
    public Long createContrato(ContratoRequestDTO request) {
        String numContrato = "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long id = finanzasRepository.createContrato(request, numContrato);
        
        finanzasRepository.generarCuotasIniciales(id);

        try {
            ContratoDetalleDTO detalle = finanzasRepository.getContratoDetalle(id);
            if (detalle != null && detalle.getCorreoResidente() != null && !detalle.getCorreoResidente().isBlank()) {
                String html = templateService.renderizar(detalle.getTipoContrato(), detalle);
                html = templateService.validarYLimpiarHtml(html);
                byte[] pdf = pdfService.generarPdf(html);
                emailService.enviarEmailContrato(detalle.getCorreoResidente(), detalle, pdf, "Contrato_" + numContrato + ".pdf");
            }
        } catch (Exception e) {
            System.err.println("Error generando PDF/Email para contrato: " + e.getMessage());
        }
        
        return id;
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

        // Enviar recibo por email (non-blocking)
        try {
            List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                "SELECT P.EMAIL FROM PERSONAS P " +
                "JOIN RESIDENTES_UNIDAD RU ON RU.ID_PERSONA = P.ID_PERSONA " +
                "WHERE RU.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL",
                new MapSqlParameterSource("u", cuota.idUnidad()));
            if (!residentes.isEmpty()) {
                String destinatario = (String) residentes.get(0).get("EMAIL");
                String referencia = "PAGO-" + request.idCuota();
                emailService.enviarReciboPago(destinatario, cuota.concepto(), cuota.valorTotal(), referencia, LocalDate.now().toString());
            }
        } catch (Exception e) {
            System.err.println("Error enviando recibo de pago: " + e.getMessage());
        }
    }

    @Override
    public ResidenteDashboardDTO getDashboardResidente(Long idResidente) {
        List<CuotaDTO> cuotas = finanzasRepository.getCuotasByResidente(idResidente);
        return new ResidenteDashboardDTO(idResidente, cuotas);
    }
}
