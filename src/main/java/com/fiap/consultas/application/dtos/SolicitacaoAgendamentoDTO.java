package com.fiap.consultas.application.dtos;

import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitacaoAgendamentoDTO {
    private String cpfPaciente;
    private String especialidade;
    private String cidade;
    private PrioridadeConsulta prioridade;
}
