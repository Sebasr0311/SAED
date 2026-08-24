package com.edificio.admin.service;

import com.edificio.admin.dao.ContratoDAO;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.enums.EstadoContrato;
import com.edificio.admin.model.enums.TipoContrato;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ContratoSuggestionService {

    private final ContratoDAO contratoDAO;

    public ContratoSuggestionService() {
        this.contratoDAO = new ContratoDAO();
    }

    public static class ContratoConfig {
        public final Integer duracionMeses;
        public final int diasAvisoPrevio;
        public final double penalizacionMeses;
        public final int diasInspeccion;
        public final String textoRenovacion;
        public final String descripcion;

        public ContratoConfig(Integer duracionMeses, int diasAvisoPrevio, double penalizacionMeses,
                              int diasInspeccion, String textoRenovacion, String descripcion) {
            this.duracionMeses = duracionMeses;
            this.diasAvisoPrevio = diasAvisoPrevio;
            this.penalizacionMeses = penalizacionMeses;
            this.diasInspeccion = diasInspeccion;
            this.textoRenovacion = textoRenovacion;
            this.descripcion = descripcion;
        }
    }

    public TipoContrato sugerirTipo(Integer idApartamento) throws SQLException {
        List<Contrato> historial = contratoDAO.findByApartamento(idApartamento);
        long exitosos = historial.stream()
            .filter(c -> c.getEstado() == EstadoContrato.ACTIVO
                      || c.getEstado() == EstadoContrato.VENCIDO)
            .count();
        if (exitosos == 0) return TipoContrato.INICIAL;
        if (exitosos >= 2) return TipoContrato.PERMANENCIA;
        return TipoContrato.RENOVACION;
    }

    public LocalDate calcularFechaFin(LocalDate inicio, TipoContrato tipo) {
        if (tipo == TipoContrato.PERMANENCIA) return null;
        int meses = (tipo == TipoContrato.INICIAL) ? 3 : 6;
        return inicio.plusMonths(meses);
    }

    public ContratoConfig getConfig(TipoContrato tipo) {
        return switch (tipo) {
            case INICIAL -> new ContratoConfig(3, 15, 1.0, 30,
                "No garantizada \u2014 sujeto a evaluaci\u00f3n", "Per\u00edodo de prueba");
            case RENOVACION -> new ContratoConfig(6, 30, 0.5, 90,
                "Evaluable \u2014 sujeto a historial", "Buen comportamiento");
            case PERMANENCIA -> new ContratoConfig(null, 60, 0.0, 180,
                "Prioritaria \u2014 continuidad autom\u00e1tica", "Residencia estable");
        };
    }
}
