package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.RespostaAgendamentoDTO;
import com.fiap.consultas.application.dtos.SolicitacaoAgendamentoDTO;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolicitarAgendamentoUseCase {

    private final ConsultaRepository consultaRepository;

    public RespostaAgendamentoDTO executar(SolicitacaoAgendamentoDTO solicitacao) {
        Consulta consulta = Consulta.builder()
                .id(UUID.randomUUID())
                .pacienteCpf(solicitacao.getCpfPaciente())
                .especialidade(solicitacao.getEspecialidade())
                .cidade(solicitacao.getCidade())
                .prioridade(solicitacao.getPrioridade())
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now())
                .dataAtualizacao(LocalDateTime.now())
                .build();

        consultaRepository.salvar(consulta);

        return RespostaAgendamentoDTO.builder()
                .consultaId(consulta.getId())
                .status(consulta.getStatus())
                .mensagem("Solicitação de agendamento recebida com sucesso.")
                .build();
    }
}