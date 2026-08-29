package com.saed.backend.reservas.service;

import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import java.util.List;

public interface ReservasService {
    List<ZonaComunDTO> getAllZonasComunes();
    List<ReservaDTO> getAllReservas();
    List<ReservaDTO> getMyReservas();
    ReservaDTO getReservaById(Long idReserva);
    Long createReserva(ReservaDTO reserva);
    void updateReservaStatus(Long idReserva, String estado);
}
