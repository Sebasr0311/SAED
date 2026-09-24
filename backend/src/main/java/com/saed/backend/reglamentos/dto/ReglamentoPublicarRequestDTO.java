package com.saed.backend.reglamentos.dto;

import java.time.LocalDate;

public class ReglamentoPublicarRequestDTO {
    private LocalDate fechaEntradaEnVigor;

    public LocalDate getFechaEntradaEnVigor() {
        return fechaEntradaEnVigor;
    }

    public void setFechaEntradaEnVigor(LocalDate fechaEntradaEnVigor) {
        this.fechaEntradaEnVigor = fechaEntradaEnVigor;
    }
}
