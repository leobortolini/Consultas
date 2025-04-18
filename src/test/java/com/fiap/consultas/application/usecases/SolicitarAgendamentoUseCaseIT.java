package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.RespostaAgendamentoDTO;
import com.fiap.consultas.application.dtos.SolicitacaoAgendamentoDTO;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase
@EnableTestBinder
class SolicitarAgendamentoUseCaseIT {

    @Autowired
    private SolicitarAgendamentoUseCase solicitarAgendamentoUseCase;

    @Autowired
    private ConsultaRepository consultaRepository;

    @Test
    void deveAgendarConsultaEPersistirNoBancoDeDados() {
        // Arrange
        SolicitacaoAgendamentoDTO solicitacao = SolicitacaoAgendamentoDTO.builder()
                .cpfPaciente("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.ALTA)
                .build();

        // Act
        RespostaAgendamentoDTO resposta = solicitarAgendamentoUseCase.executar(solicitacao);

        // Assert
        assertNotNull(resposta);
        assertNotNull(resposta.getConsultaId());
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, resposta.getStatus());

        // Verificar se a consulta foi realmente persistida
        Optional<Consulta> consultaSalva = consultaRepository.buscarPorId(resposta.getConsultaId());
        assertTrue(consultaSalva.isPresent());
        assertEquals(solicitacao.getCpfPaciente(), consultaSalva.get().getPacienteCpf());
        assertEquals(solicitacao.getEspecialidade(), consultaSalva.get().getEspecialidade());
        assertEquals(solicitacao.getCidade(), consultaSalva.get().getCidade());
        assertEquals(solicitacao.getPrioridade(), consultaSalva.get().getPrioridade());
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, consultaSalva.get().getStatus());
    }

    @Test
    void deveManterConsistenciaDeDadosEntreRequestEConsultaPersistida() {
        // Arrange
        String cpf = "98765432100";
        String especialidade = "OFTALMOLOGIA";
        String cidade = "Rio de Janeiro";
        PrioridadeConsulta alta = PrioridadeConsulta.ALTA;

        SolicitacaoAgendamentoDTO solicitacao = SolicitacaoAgendamentoDTO.builder()
                .cpfPaciente(cpf)
                .especialidade(especialidade)
                .cidade(cidade)
                .prioridade(alta)
                .build();

        // Act
        RespostaAgendamentoDTO resposta = solicitarAgendamentoUseCase.executar(solicitacao);
        UUID consultaId = resposta.getConsultaId();

        // Assert
        Optional<Consulta> consultaSalva = consultaRepository.buscarPorId(consultaId);
        assertTrue(consultaSalva.isPresent());

        Consulta consulta = consultaSalva.get();
        assertEquals(cpf, consulta.getPacienteCpf());
        assertEquals(especialidade, consulta.getEspecialidade());
        assertEquals(cidade, consulta.getCidade());
        assertEquals(alta, consulta.getPrioridade());
    }

    @Test
    void deveCriarConsultasComIdsUnicos() {
        // Arrange
        SolicitacaoAgendamentoDTO solicitacao1 = SolicitacaoAgendamentoDTO.builder()
                .cpfPaciente("11111111111")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.ALTA)
                .build();

        SolicitacaoAgendamentoDTO solicitacao2 = SolicitacaoAgendamentoDTO.builder()
                .cpfPaciente("22222222222")
                .especialidade("ORTOPEDIA")
                .cidade("Belo Horizonte")
                .prioridade(PrioridadeConsulta.MEDIA)
                .build();

        // Act
        RespostaAgendamentoDTO resposta1 = solicitarAgendamentoUseCase.executar(solicitacao1);
        RespostaAgendamentoDTO resposta2 = solicitarAgendamentoUseCase.executar(solicitacao2);

        // Assert
        assertNotEquals(resposta1.getConsultaId(), resposta2.getConsultaId());

        // Verifique se ambas foram persistidas corretamente
        assertTrue(consultaRepository.buscarPorId(resposta1.getConsultaId()).isPresent());
        assertTrue(consultaRepository.buscarPorId(resposta2.getConsultaId()).isPresent());
    }
}