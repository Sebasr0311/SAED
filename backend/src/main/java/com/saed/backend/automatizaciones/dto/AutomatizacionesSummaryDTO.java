package com.saed.backend.automatizaciones.dto;

public class AutomatizacionesSummaryDTO {
    private Long totalReglas;
    private Long reglasActivas;
    private Long reglasInactivas;
    private Long totalEventos;
    private Long totalEjecucionesMes;
    private Long ejecucionesExitosas;
    private Long ejecucionesFallidas;
    private Double tasaExito;

    public AutomatizacionesSummaryDTO() {}

    public AutomatizacionesSummaryDTO(Long totalReglas, Long reglasActivas, Long reglasInactivas,
                                     Long totalEventos, Long totalEjecucionesMes,
                                     Long ejecucionesExitosas, Long ejecucionesFallidas, Double tasaExito) {
        this.totalReglas = totalReglas;
        this.reglasActivas = reglasActivas;
        this.reglasInactivas = reglasInactivas;
        this.totalEventos = totalEventos;
        this.totalEjecucionesMes = totalEjecucionesMes;
        this.ejecucionesExitosas = ejecucionesExitosas;
        this.ejecucionesFallidas = ejecucionesFallidas;
        this.tasaExito = tasaExito;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long totalReglas;
        private Long reglasActivas;
        private Long reglasInactivas;
        private Long totalEventos;
        private Long totalEjecucionesMes;
        private Long ejecucionesExitosas;
        private Long ejecucionesFallidas;
        private Double tasaExito;

        public Builder totalReglas(Long totalReglas) { this.totalReglas = totalReglas; return this; }
        public Builder reglasActivas(Long reglasActivas) { this.reglasActivas = reglasActivas; return this; }
        public Builder reglasInactivas(Long reglasInactivas) { this.reglasInactivas = reglasInactivas; return this; }
        public Builder totalEventos(Long totalEventos) { this.totalEventos = totalEventos; return this; }
        public Builder totalEjecucionesMes(Long totalEjecucionesMes) { this.totalEjecucionesMes = totalEjecucionesMes; return this; }
        public Builder ejecucionesExitosas(Long ejecucionesExitosas) { this.ejecucionesExitosas = ejecucionesExitosas; return this; }
        public Builder ejecucionesFallidas(Long ejecucionesFallidas) { this.ejecucionesFallidas = ejecucionesFallidas; return this; }
        public Builder tasaExito(Double tasaExito) { this.tasaExito = tasaExito; return this; }

        public AutomatizacionesSummaryDTO build() {
            return new AutomatizacionesSummaryDTO(totalReglas, reglasActivas, reglasInactivas, totalEventos,
                    totalEjecucionesMes, ejecucionesExitosas, ejecucionesFallidas, tasaExito);
        }
    }

    public Long getTotalReglas() { return totalReglas; }
    public void setTotalReglas(Long totalReglas) { this.totalReglas = totalReglas; }

    public Long getReglasActivas() { return reglasActivas; }
    public void setReglasActivas(Long reglasActivas) { this.reglasActivas = reglasActivas; }

    public Long getReglasInactivas() { return reglasInactivas; }
    public void setReglasInactivas(Long reglasInactivas) { this.reglasInactivas = reglasInactivas; }

    public Long getTotalEventos() { return totalEventos; }
    public void setTotalEventos(Long totalEventos) { this.totalEventos = totalEventos; }

    public Long getTotalEjecucionesMes() { return totalEjecucionesMes; }
    public void setTotalEjecucionesMes(Long totalEjecucionesMes) { this.totalEjecucionesMes = totalEjecucionesMes; }

    public Long getEjecucionesExitosas() { return ejecucionesExitosas; }
    public void setEjecucionesExitosas(Long ejecucionesExitosas) { this.ejecucionesExitosas = ejecucionesExitosas; }

    public Long getEjecucionesFallidas() { return ejecucionesFallidas; }
    public void setEjecucionesFallidas(Long ejecucionesFallidas) { this.ejecucionesFallidas = ejecucionesFallidas; }

    public Double getTasaExito() { return tasaExito; }
    public void setTasaExito(Double tasaExito) { this.tasaExito = tasaExito; }
}
