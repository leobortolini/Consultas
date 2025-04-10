package com.fiap.consultas.application.dtos;

import com.fiap.consultas.domain.enums.StatusConsulta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespostaAgendamentoDTO {
    private UUID consultaId;
    private StatusConsulta status;
    private String mensagem;
}
