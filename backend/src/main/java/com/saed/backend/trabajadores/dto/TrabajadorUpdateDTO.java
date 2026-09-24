package com.saed.backend.trabajadores.dto;

import java.time.LocalDate;

public record TrabajadorUpdateDTO(
        String oficioEspecialidad,
        String arlAseguradora,
        LocalDate arlFechaAfiliacion,
        LocalDate arlFechaVencimiento,
        LocalDate certAlturasVencimiento,
        String empresaIndependiente,
        String estado
) {}
