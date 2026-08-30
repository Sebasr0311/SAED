package com.saed.backend.reservas.service.impl;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import com.saed.backend.reservas.repository.ReservasRepository;
import com.saed.backend.reservas.service.ReservasService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReservasServiceImpl implements ReservasService {

    private final ReservasRepository reservasRepository;

    public ReservasServiceImpl(ReservasRepository reservasRepository) {
        this.reservasRepository = reservasRepository;
    }

    @Override
    public List<ZonaComunDTO> getAllZonasComunes() {
        return reservasRepository.findAllZonas();
    }

    @Override
    public List<ReservaDTO> getAllReservas() {
        return reservasRepository.findAllReservas();
    }

    @Override
    public List<ReservaDTO> getMyReservas() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        return reservasRepository.findReservasByPersona(idUsuario);
    }

    @Override
    public ReservaDTO getReservaById(Long idReserva) {
        return reservasRepository.findReservaById(idReserva)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));
    }

    @Override
    public Long createReserva(ReservaDTO reserva) {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        
        if (idUsuario == null || idPropiedad == null) {
            throw new IllegalStateException("Contexto de usuario inválido.");
        }
        
        reserva.setIdPersonaSolicita(idUsuario); // We pass idUsuario to the repository which will translate it
        
        // Unidades might be missing in MVP Request, ideally we fetch the user's unit.
        // For MVP, we set a fallback or assume it is sent from frontend.
        if (reserva.getIdUnidad() == null) {
            // For MVP fallback (assuming user is tied to unit 1 for now if missing)
            reserva.setIdUnidad(1L); 
        }

        reserva.setEstado("PENDIENTE");
        return reservasRepository.createReserva(reserva, idPropiedad);
    }

    @Override
    public void updateReservaStatus(Long idReserva, String estado) {
        Long idPersona = SaedContextHolder.getContext().getUserId();
        // Validation that reserva exists within tenant (RLS handles implicit check)
        getReservaById(idReserva); 
        
        Long aprobadoPor = null;
        if ("APROBADA".equals(estado) || "RECHAZADA".equals(estado)) {
            aprobadoPor = idPersona;
        }
        reservasRepository.updateEstadoReserva(idReserva, estado, aprobadoPor);
    }
}
