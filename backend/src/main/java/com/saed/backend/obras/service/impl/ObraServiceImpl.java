package com.saed.backend.obras.service.impl;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.obras.dto.ObraDTO;
import com.saed.backend.obras.repository.ObraRepository;
import com.saed.backend.obras.service.ObraService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ObraServiceImpl implements ObraService {

    private final ObraRepository obraRepository;

    public ObraServiceImpl(ObraRepository obraRepository) {
        this.obraRepository = obraRepository;
    }

    @Override
    public List<ObraDTO> getObrasAdmin() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        return obraRepository.findAllByPropiedad(idPropiedad);
    }

    @Override
    public List<ObraDTO> getMisObras() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long idUnidad = SaedContextHolder.getContext().getUnitId();
        if (idUnidad == null) {
            throw new SecurityException("Usuario no tiene unidad asignada");
        }
        return obraRepository.findAllByUnidad(idUnidad, idPropiedad);
    }

    @Override
    public ObraDTO getObraById(Long idObra) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        ObraDTO obra = obraRepository.findById(idObra, idPropiedad)
                .orElseThrow(() -> new IllegalArgumentException("Obra no encontrada o acceso denegado"));
                
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        boolean isAdmin = "SUPERADMIN".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode);
        
        if (!isAdmin) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null || !userUnitId.equals(obra.getIdUnidad())) {
                throw new SecurityException("Acceso denegado: no tiene permisos para ver esta obra");
            }
        }
        
        return obra;
    }

    @Override
    public Long solicitarObra(ObraDTO request) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long solicitadoPor = SaedContextHolder.getContext().getUserId();
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        boolean isAdmin = "SUPERADMIN".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode);
        
        if (!isAdmin) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null) {
                throw new SecurityException("Usuario no tiene unidad asignada");
            }
            // Strict IDOR prevention: force the unit ID from context for residents
            request.setIdUnidad(userUnitId);
        } else {
            if (request.getIdUnidad() == null) {
                throw new IllegalArgumentException("Se requiere especificar una unidad para la obra");
            }
        }

        if (request.getFechaInicio() == null || request.getFechaFinEstimada() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin estimada son obligatorias");
        }

        if (request.getFechaFinEstimada().isBefore(request.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio");
        }
        
        if (request.getDepositoGarantia() == null) {
            request.setDepositoGarantia(java.math.BigDecimal.ZERO);
        } else if (request.getDepositoGarantia().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El deposito de garantia no puede ser negativo");
        }

        return obraRepository.createObra(request, idPropiedad, solicitadoPor);
    }

    @Override
    public void aprobarObra(Long idObra) {
        cambiarEstadoObra(idObra, "APROBADA");
    }

    @Override
    public void rechazarObra(Long idObra) {
        cambiarEstadoObra(idObra, "RECHAZADA");
    }

    @Override
    public void finalizarObra(Long idObra) {
        cambiarEstadoObra(idObra, "FINALIZADA");
    }

    private void cambiarEstadoObra(Long idObra, String estado) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long aprobadoPor = SaedContextHolder.getContext().getUserId();
        
        // Ensure obra exists and belongs to property
        ObraDTO obra = getObraById(idObra);
        
        // For security, only allow state transition if logic permits
        if ("FINALIZADA".equals(obra.getEstado())) {
            throw new IllegalStateException("La obra ya esta finalizada");
        }
        
        obraRepository.updateEstado(idObra, idPropiedad, estado, aprobadoPor);
    }
}
