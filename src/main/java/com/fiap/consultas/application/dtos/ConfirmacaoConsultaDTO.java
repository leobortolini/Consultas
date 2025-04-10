package com.fiap.consultas.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmacaoConsultaDTO {
    private String consultaId;
    private boolean confirmada;
}