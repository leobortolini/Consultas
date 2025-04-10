package com.fiap.consultas.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioTrabalho {
    private DayOfWeek diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFim;
}