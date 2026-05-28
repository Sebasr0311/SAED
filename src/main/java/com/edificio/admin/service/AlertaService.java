package com.edificio.admin.service;

import com.edificio.admin.dao.AlertaPagoDAO;
import com.edificio.admin.dao.CuotaArriendoDAO;
import com.edificio.admin.model.AlertaPago;
import com.edificio.admin.model.CuotaArriendo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class AlertaService {

    private final CuotaArriendoDAO cuotaDAO = new CuotaArriendoDAO();
    private final AlertaPagoDAO alertaDAO = new AlertaPagoDAO();

    /**
     * Genera alertas de PROXIMO_VENCIMIENTO para cuotas PENDIENTES
     * cuya fecha_limite esté a 3 o 1 día de la fecha actual.
     * El unique index UIX_ALERTA_CUOTA_DIA evita duplicados.
     */
    public void generarAlertasProximoVencimiento() {
        try {
            List<CuotaArriendo> cuotas = cuotaDAO.findPendientes();
            LocalDate hoy = LocalDate.now();

            for (CuotaArriendo c : cuotas) {
                if (c.getFechaLimite() == null) continue;
                long dias = ChronoUnit.DAYS.between(hoy, c.getFechaLimite());

                if (dias == 3 || dias == 1) {
                    try {
                        alertaDAO.insert(c.getIdCuota(),
                            AlertaPago.TipoAlerta.PROXIMO_VENCIMIENTO,
                            AlertaPago.Canal.SISTEMA);
                    } catch (Exception ignored) {
                        // unique index evita duplicados
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AlertaService] Error al generar alertas: " + e.getMessage());
        }
    }
}
