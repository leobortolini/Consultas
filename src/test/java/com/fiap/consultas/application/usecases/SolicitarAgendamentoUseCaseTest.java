package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.RespostaAgendamentoDTO;
import com.fiap.consultas.application.dtos.SolicitacaoAgendamentoDTO;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolicitarAgendamentoUseCaseTest {

    @Mock
    private ConsultaRepository consultaRepository;

    @InjectMocks
    private SolicitarAgendamentoUseCase solicitarAgendamentoUseCase;

    @Captor
    private ArgumentCaptor<Consulta> consultaCaptor;

    private SolicitacaoAgendamentoDTO solicitacao;

    @BeforeEach
    void setUp() {
        solicitacao = SolicitacaoAgendamentoDTO.builder()
                .cpfPaciente("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.ALTA)
                .build();
    }

    @Test
    void deveCriarConsultaComStatusPendenteAgendamento() {
        // Act
        solicitarAgendamentoUseCase.executar(solicitacao);

        // Assert
        verify(consultaRepository).salvar(consultaCaptor.capture());
        Consulta consultaSalva = consultaCaptor.getValue();

        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, consultaSalva.getStatus());
        assertEquals(solicitacao.getCpfPaciente(), consultaSalva.getPacienteCpf());
        assertEquals(solicitacao.getEspecialidade(), consultaSalva.getEspecialidade());
        assertEquals(solicitacao.getCidade(), consultaSalva.getCidade());
        assertEquals(solicitacao.getPrioridade(), consultaSalva.getPrioridade());
        assertNotNull(consultaSalva.getId());
        assertNotNull(consultaSalva.getDataCriacao());
        assertNotNull(consultaSalva.getDataAtualizacao());
    }

    @Test
    void deveRetornarRespostaAgendamentoCorreta() {
        // Act
        RespostaAgendamentoDTO resposta = solicitarAgendamentoUseCase.executar(solicitacao);

        // Assert
        assertNotNull(resposta);
        assertNotNull(resposta.getConsultaId());
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, resposta.getStatus());
        assertEquals("Solicitação de agendamento recebida com sucesso.", resposta.getMensagem());
    }

    @Test
    void deveChamarRepositorioParaSalvarConsulta() {
        // Act
        solicitarAgendamentoUseCase.executar(solicitacao);

        // Assert
        verify(consultaRepository, times(1)).salvar(any(Consulta.class));
    }

    @Test
    void deveDefinirDataCriacaoEDataAtualizacaoParaDataAtual() {
        // Given
        LocalDateTime antes = LocalDateTime.now();

        // Act
        solicitarAgendamentoUseCase.executar(solicitacao);

        // Assert
        verify(consultaRepository).salvar(consultaCaptor.capture());
        Consulta consultaSalva = consultaCaptor.getValue();

        LocalDateTime depois = LocalDateTime.now();

        assertTrue(
                !consultaSalva.getDataCriacao().isBefore(antes) &&
                        !consultaSalva.getDataCriacao().isAfter(depois)
        );

        assertTrue(
                !consultaSalva.getDataAtualizacao().isBefore(antes) &&
                        !consultaSalva.getDataAtualizacao().isAfter(depois)
        );
    }

    @Test
    void deveGerarIdUnicoParaCadaExecucao() {
        // Act
        RespostaAgendamentoDTO resposta1 = solicitarAgendamentoUseCase.executar(solicitacao);
        RespostaAgendamentoDTO resposta2 = solicitarAgendamentoUseCase.executar(solicitacao);

        // Assert
        assertNotEquals(resposta1.getConsultaId(), resposta2.getConsultaId());
    }
}