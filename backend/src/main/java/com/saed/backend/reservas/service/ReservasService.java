package com.saed.backend.reservas.service;

import com.saed.backend.reservas.dto.CreateZonaComunDTO;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.UpdateZonaComunDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import java.util.List;

public interface ReservasService {
    // --- Zonas Comunes (GAP-F8-05) ---
    List<ZonaComunDTO> getAllZonasComunes();
    ZonaComunDTO getZonaComunById(Long idZona);
    ZonaComunDTO createZonaComun(CreateZonaComunDTO dto);
    ZonaComunDTO updateZonaComun(Long idZona, UpdateZonaComunDTO dto);
    void deleteZonaComun(Long idZona);

    // --- Reservas ---
    List<ReservaDTO> getAllReservas();
    List<ReservaDTO> getMyReservas();
    ReservaDTO getReservaById(Long idReserva);
    Long createReserva(ReservaDTO reserva);
    void updateReservaStatus(Long idReserva, String estado);
    void cancelReserva(Long idReserva);

    // --- Estado de Mora Preventivo (GAP-F8-08) ---
    com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO getMiEstadoMora();
}
