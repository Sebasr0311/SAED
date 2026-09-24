package com.saed.backend.seguros.service;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.dto.ResumenPolizasDTO;
import com.saed.backend.seguros.repository.PolizaSeguroRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Servicio de dominio para la gestión y cálculo dinámico de Pólizas de Seguro (F10-07).
 */
@Service
public class PolizaSeguroService {

    public static final int DEFAULT_DIAS_ALERTA = 45;

    private final PolizaSeguroRepository repository;

    public PolizaSeguroService(PolizaSeguroRepository repository) {
        this.repository = repository;
    }

    /**
     * Regla de dominio canónica y unificada para el cálculo del estado dinámico temporal (OBS-01).
     *
     * Prioridad:
     * 1. CANCELADA: estado persistido manual prioritario sobre cualquier temporalidad.
     * 2. VENCIDA: fechaReferencia > fechaFin.
     * 3. POR_VENCER: fechaReferencia >= (fechaFin - diasAlerta) AND fechaReferencia <= fechaFin.
     * 4. VIGENTE: todos los demás casos (incluyendo fechaReferencia < fechaInicio o fuera de alerta).
     */
    public static String calcularEstadoDinamico(String estadoPersistido, LocalDate fechaInicio, LocalDate fechaFin, Integer diasAlerta, LocalDate fechaReferencia) {
        if (estadoPersistido != null && "CANCELADA".equalsIgnoreCase(estadoPersistido.trim())) {
            return "CANCELADA";
        }
        if (fechaFin == null) {
            return "VIGENTE";
        }
        LocalDate ref = fechaReferencia != null ? fechaReferencia : LocalDate.now();
        int alerta = (diasAlerta != null && diasAlerta > 0) ? diasAlerta : DEFAULT_DIAS_ALERTA;

        if (ref.isAfter(fechaFin)) {
            return "VENCIDA";
        }
        LocalDate umbralAlerta = fechaFin.minusDays(alerta);
        if (!ref.isBefore(umbralAlerta) && !ref.isAfter(fechaFin)) {
            return "POR_VENCER";
        }
        return "VIGENTE";
    }

    public static String calcularEstadoDinamico(PolizaSeguroDTO dto, LocalDate fechaReferencia) {
        if (dto == null) return "VIGENTE";
        return calcularEstadoDinamico(dto.getEstado(), dto.getFechaInicio(), dto.getFechaFin(), dto.getDiasAlertaVencimiento(), fechaReferencia);
    }

    public static String calcularEstadoDinamico(PolizaSeguroDTO dto) {
        return calcularEstadoDinamico(dto, LocalDate.now());
    }

    public List<PolizaSeguroDTO> getAllPolizas() {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        List<PolizaSeguroDTO> polizas = repository.findAllByPropiedad(propiedadId);
        polizas.forEach(p -> p.setEstado(calcularEstadoDinamico(p)));
        return polizas;
    }

    public List<PolizaSeguroDTO> getPolizasVigentesResidentes() {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        List<PolizaSeguroDTO> all = repository.findAllByPropiedad(propiedadId);
        return all.stream()
                .peek(p -> p.setEstado(calcularEstadoDinamico(p)))
                .filter(p -> "VIGENTE".equals(p.getEstado()) || "POR_VENCER".equals(p.getEstado()))
                .toList();
    }

    public ResumenPolizasDTO getResumen() {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        List<PolizaSeguroDTO> polizas = repository.findAllByPropiedad(propiedadId);
        ResumenPolizasDTO resumen = new ResumenPolizasDTO();
        resumen.setTotalPolizas(polizas.size());

        int vigentes = 0;
        int porVencer = 0;
        int vencidas = 0;
        int canceladas = 0;
        BigDecimal valorAseguradoTotal = BigDecimal.ZERO;
        BigDecimal primaAnualTotal = BigDecimal.ZERO;

        for (PolizaSeguroDTO p : polizas) {
            String estado = calcularEstadoDinamico(p);
            p.setEstado(estado);
            switch (estado) {
                case "VIGENTE" -> {
                    vigentes++;
                    if (p.getValorAsegurado() != null) valorAseguradoTotal = valorAseguradoTotal.add(p.getValorAsegurado());
                    if (p.getValorPrimaAnual() != null) primaAnualTotal = primaAnualTotal.add(p.getValorPrimaAnual());
                }
                case "POR_VENCER" -> {
                    porVencer++;
                    if (p.getValorAsegurado() != null) valorAseguradoTotal = valorAseguradoTotal.add(p.getValorAsegurado());
                    if (p.getValorPrimaAnual() != null) primaAnualTotal = primaAnualTotal.add(p.getValorPrimaAnual());
                }
                case "VENCIDA" -> vencidas++;
                case "CANCELADA" -> canceladas++;
            }
        }

        resumen.setVigentes(vigentes);
        resumen.setPorVencer(porVencer);
        resumen.setVencidas(vencidas);
        resumen.setCanceladas(canceladas);
        resumen.setValorAseguradoTotal(valorAseguradoTotal);
        resumen.setPrimaAnualTotal(primaAnualTotal);
        return resumen;
    }

    public PolizaSeguroDTO getPolizaById(Long id) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        PolizaSeguroDTO dto = repository.findByIdAndPropiedad(id, propiedadId)
                .orElseThrow(() -> new IllegalArgumentException("Póliza no encontrada con ID: " + id));
        dto.setEstado(calcularEstadoDinamico(dto));
        return dto;
    }

    public void createPoliza(PolizaSeguroDTO dto) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        validarReglasNegocio(dto, propiedadId);
        dto.setIdPropiedad(propiedadId);
        if (dto.getDiasAlertaVencimiento() == null || dto.getDiasAlertaVencimiento() <= 0) {
            dto.setDiasAlertaVencimiento(DEFAULT_DIAS_ALERTA);
        }
        dto.setEstado(calcularEstadoDinamico(dto));
        repository.insert(dto);
    }

    public void updatePoliza(Long id, PolizaSeguroDTO dto) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        validarReglasNegocio(dto, propiedadId);
        dto.setIdPoliza(id);
        dto.setIdPropiedad(propiedadId);
        if (dto.getDiasAlertaVencimiento() == null || dto.getDiasAlertaVencimiento() <= 0) {
            dto.setDiasAlertaVencimiento(DEFAULT_DIAS_ALERTA);
        }
        dto.setEstado(calcularEstadoDinamico(dto));
        repository.update(dto);
    }

    public void delete(Long id) {
        deletePoliza(id);
    }

    public void deletePoliza(Long id) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        repository.delete(id, propiedadId);
    }

    private void validarReglasNegocio(PolizaSeguroDTO dto, Long propiedadId) {
        if (dto.getNumeroPoliza() == null || dto.getNumeroPoliza().trim().isBlank()) {
            throw new IllegalArgumentException("El número de póliza es obligatorio.");
        }
        if (dto.getCompaniaAseguradora() == null || dto.getCompaniaAseguradora().trim().isBlank()) {
            throw new IllegalArgumentException("La compañía aseguradora es obligatoria.");
        }
        if (dto.getValorAsegurado() == null || dto.getValorAsegurado().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor asegurado debe ser un monto mayor a cero.");
        }
        if (dto.getValorPrimaAnual() == null || dto.getValorPrimaAnual().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El valor de la prima anual no puede ser negativo.");
        }
        if (dto.getFechaInicio() != null && dto.getFechaFin() != null && dto.getFechaFin().isBefore(dto.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de fin de vigencia debe ser posterior o igual a la fecha de inicio.");
        }
        if (dto.getIdDocumento() != null) {
            boolean valido = repository.validarDocumentoPerteneceAPropiedad(dto.getIdDocumento(), propiedadId);
            if (!valido) {
                throw new IllegalArgumentException("El documento asociado (ID " + dto.getIdDocumento() + ") no existe o no pertenece a esta propiedad.");
            }
        }
    }
}
