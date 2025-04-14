package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.application.dtos.PacienteDTO;
import com.fiap.consultas.application.ports.NotificacaoServicePort;
import com.fiap.consultas.application.ports.PacienteServicePort;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.enums.TipoNotificacao;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureTestDatabase
class EnviarNotificacaoConfirmacaoUseCaseIT {

    @Autowired
    private EnviarNotificacaoConfirmacaoUseCase useCase;

    @Autowired
    private ConsultaRepository consultaRepository;

    @MockitoBean
    private NotificacaoServicePort notificacaoService;

    @MockitoBean
    private PacienteServicePort pacienteService;

    @Captor
    private ArgumentCaptor<NotificacaoDTO> notificacaoCaptor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Consulta consultaAgendada;
    private Consulta consultaConfirmada;
    private PacienteDTO paciente;
    private String pacienteCpf;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM consultas");

        pacienteCpf = "12345678900";
        paciente = PacienteDTO.builder()
                .nome("João Silva")
                .email("joao@exemplo.com")
                .telefone("11999887766")
                .cpf(pacienteCpf)
                .build();

        when(pacienteService.buscarPacientePorCpf(pacienteCpf)).thenReturn(paciente);

        LocalDateTime dataAtual = LocalDateTime.now();

        consultaAgendada = new Consulta();
        UUID consultaId = UUID.randomUUID();
        consultaAgendada.setId(consultaId);
        consultaAgendada.setDataHora(dataAtual.plusWeeks(2));
        consultaAgendada.setPacienteCpf(pacienteCpf);
        consultaAgendada.setCidade("CIDADE");
        consultaAgendada.setLocalConsulta("Consultório A");
        consultaAgendada.setStatus(StatusConsulta.AGENDADA);
        consultaAgendada.setDataAtualizacao(LocalDateTime.now());
        consultaAgendada.setDataCriacao(LocalDateTime.now());
        consultaAgendada.setEspecialidade("ESPECIALIDADE");
        consultaAgendada.setPrioridade(PrioridadeConsulta.ALTA);
        consultaAgendada = consultaRepository.salvar(consultaAgendada);

        consultaConfirmada = new Consulta();
        UUID consultaConfirmadaId = UUID.randomUUID();
        consultaConfirmada.setId(consultaConfirmadaId);
        consultaConfirmada.setDataHora(dataAtual.plusDays(1));
        consultaConfirmada.setPacienteCpf(pacienteCpf);
        consultaConfirmada.setCidade("CIDADE");
        consultaConfirmada.setLocalConsulta("Consultório B");
        consultaConfirmada.setStatus(StatusConsulta.CONFIRMADA);
        consultaConfirmada.setDataAtualizacao(LocalDateTime.now());
        consultaConfirmada.setDataCriacao(LocalDateTime.now());
        consultaConfirmada.setEspecialidade("ESPECIALIDADE");
        consultaConfirmada.setPrioridade(PrioridadeConsulta.ALTA);
        consultaConfirmada = consultaRepository.salvar(consultaConfirmada);
    }

    @Test
    void deveEnviarNotificacaoParaConsultasAgendadasEm2Semanas() {
        // Act
        useCase.executar();

        // Assert
        verify(notificacaoService).enviarNotificacao(notificacaoCaptor.capture());
        NotificacaoDTO notificacao = notificacaoCaptor.getValue();

        assertEquals(paciente.getNome(), notificacao.getNomePaciente());
        assertEquals(paciente.getEmail(), notificacao.getEmail());
        assertEquals(paciente.getTelefone(), notificacao.getTelefone());
        assertEquals(consultaAgendada.getId().toString(), notificacao.getConsulta());
        assertEquals(consultaAgendada.getLocalConsulta(), notificacao.getLocalConsulta());
        assertDatasIguaisIgnorandoNanos(consultaAgendada.getDataHora().toString(), notificacao.getDataConsulta());
        assertEquals(TipoNotificacao.CONFIRMACAO_CONSULTA, notificacao.getTipoNotificacao());
    }

    @Test
    void deveEnviarLembreteParaConsultasConfirmadasParaAmanha() {
        // Act
        useCase.enviarLembreteDiaAnterior();

        // Assert
        verify(notificacaoService).enviarNotificacao(notificacaoCaptor.capture());
        NotificacaoDTO notificacao = notificacaoCaptor.getValue();

        assertEquals(paciente.getNome(), notificacao.getNomePaciente());
        assertEquals(paciente.getEmail(), notificacao.getEmail());
        assertEquals(paciente.getTelefone(), notificacao.getTelefone());
        assertEquals(consultaConfirmada.getId().toString(), notificacao.getConsulta());
        assertEquals(consultaConfirmada.getLocalConsulta(), notificacao.getLocalConsulta());
        assertDatasIguaisIgnorandoNanos(consultaConfirmada.getDataHora().toString(), notificacao.getDataConsulta());
        assertEquals(TipoNotificacao.AVISO_UM_DIA_ANTES, notificacao.getTipoNotificacao());
    }

    @Test
    void naoDeveEnviarNotificacaoParaConsultasForaDoPeriodo() {
        // Arrange
        Consulta consultaDistante = new Consulta();
        consultaDistante.setId(UUID.randomUUID());
        consultaDistante.setDataHora(LocalDateTime.now().plusWeeks(3)); // 3 semanas no futuro
        consultaDistante.setPacienteCpf(pacienteCpf);
        consultaDistante.setCidade("CIDADE");
        consultaDistante.setLocalConsulta("Consultório C");
        consultaDistante.setStatus(StatusConsulta.AGENDADA);
        consultaDistante.setDataAtualizacao(LocalDateTime.now());
        consultaDistante.setDataCriacao(LocalDateTime.now());
        consultaDistante.setEspecialidade("ESPECIALIDADE");
        consultaDistante.setPrioridade(PrioridadeConsulta.ALTA);
        consultaRepository.salvar(consultaDistante);

        // Act
        useCase.executar();

        // Assert
        verify(notificacaoService, times(1)).enviarNotificacao(any());
    }

    @Test
    void naoDeveEnviarLembreteParaConsultasNaoAgendadasParaAmanha() {
        // Arrange
        Consulta consultaOutroDia = new Consulta();
        consultaOutroDia.setId(UUID.randomUUID());
        consultaOutroDia.setDataHora(LocalDateTime.now().plusDays(2)); // 2 dias no futuro
        consultaOutroDia.setPacienteCpf(pacienteCpf);
        consultaOutroDia.setLocalConsulta("Consultório D");
        consultaOutroDia.setCidade("CIDADE");
        consultaOutroDia.setStatus(StatusConsulta.CONFIRMADA);
        consultaOutroDia.setDataAtualizacao(LocalDateTime.now());
        consultaOutroDia.setDataCriacao(LocalDateTime.now());
        consultaOutroDia.setEspecialidade("ESPECIALIDADE");
        consultaOutroDia.setPrioridade(PrioridadeConsulta.ALTA);

        consultaRepository.salvar(consultaOutroDia);

        // Act
        useCase.enviarLembreteDiaAnterior();

        // Assert
        verify(notificacaoService, times(1)).enviarNotificacao(any());
    }

    @Test
    void deveEnviarNotificacaoParaMultiplasConsultasNoPeriodo() {
        // Arrange
        Consulta outraConsultaAgendada = new Consulta();
        outraConsultaAgendada.setId(UUID.randomUUID());
        outraConsultaAgendada.setDataHora(LocalDateTime.now().plusWeeks(2).minusDays(1)); // 13 dias no futuro
        outraConsultaAgendada.setPacienteCpf(pacienteCpf);
        outraConsultaAgendada.setLocalConsulta("Consultório E");
        outraConsultaAgendada.setStatus(StatusConsulta.AGENDADA);
        outraConsultaAgendada.setCidade("CIDADE");
        outraConsultaAgendada.setDataAtualizacao(LocalDateTime.now());
        outraConsultaAgendada.setDataCriacao(LocalDateTime.now());
        outraConsultaAgendada.setEspecialidade("ESPECIALIDADE");
        outraConsultaAgendada.setPrioridade(PrioridadeConsulta.ALTA);

        consultaRepository.salvar(outraConsultaAgendada);

        // Act
        useCase.executar();

        // Assert
        verify(notificacaoService, times(2)).enviarNotificacao(any());
    }

    @Test
    void deveEnviarLembreteParaMultiplasConsultasDeAmanha() {
        // Arrange
        Consulta outraConsultaAmanha = new Consulta();
        outraConsultaAmanha.setId(UUID.randomUUID());
        outraConsultaAmanha.setDataHora(LocalDateTime.now().plusDays(1).withHour(14)); // Amanhã às 14h
        outraConsultaAmanha.setPacienteCpf(pacienteCpf);
        outraConsultaAmanha.setCidade("CIDADE");
        outraConsultaAmanha.setLocalConsulta("Consultório F");
        outraConsultaAmanha.setStatus(StatusConsulta.CONFIRMADA);
        outraConsultaAmanha.setDataAtualizacao(LocalDateTime.now());
        outraConsultaAmanha.setDataCriacao(LocalDateTime.now());
        outraConsultaAmanha.setEspecialidade("ESPECIALIDADE");
        outraConsultaAmanha.setPrioridade(PrioridadeConsulta.ALTA);

        consultaRepository.salvar(outraConsultaAmanha);

        // Act
        useCase.enviarLembreteDiaAnterior();

        // Assert
        verify(notificacaoService, times(2)).enviarNotificacao(any());
    }

    @Test
    void naoDeveEnviarNotificacaoQuandoNaoHaConsultas() {
        jdbcTemplate.execute("DELETE FROM consultas");

        // Act
        useCase.executar();
        useCase.enviarLembreteDiaAnterior();

        // Assert
        verify(notificacaoService, never()).enviarNotificacao(any());
    }

    @Test
    void deveConsiderarCorretamenteDiferencaDeDatas() {
        // Arrange
        LocalDateTime dataAtual = LocalDateTime.now();
        LocalDateTime duasSemanasFuturo = dataAtual.plusWeeks(2);

        Consulta consultaExata = new Consulta();
        consultaExata.setId(UUID.randomUUID());
        consultaExata.setDataHora(duasSemanasFuturo);
        consultaExata.setPacienteCpf(pacienteCpf);
        consultaExata.setLocalConsulta("Consultório G");
        consultaExata.setStatus(StatusConsulta.AGENDADA);
        consultaExata.setCidade("CIDADE");
        consultaExata.setDataAtualizacao(LocalDateTime.now());
        consultaExata.setDataCriacao(LocalDateTime.now());
        consultaExata.setEspecialidade("ESPECIALIDADE");
        consultaExata.setPrioridade(PrioridadeConsulta.ALTA);

        consultaRepository.salvar(consultaExata);

        Consulta consultaUmDiaAntes = new Consulta();
        consultaUmDiaAntes.setId(UUID.randomUUID());
        consultaUmDiaAntes.setDataHora(duasSemanasFuturo.minusDays(1));
        consultaUmDiaAntes.setPacienteCpf(pacienteCpf);
        consultaUmDiaAntes.setLocalConsulta("Consultório H");
        consultaUmDiaAntes.setStatus(StatusConsulta.AGENDADA);
        consultaUmDiaAntes.setCidade("CIDADE");
        consultaUmDiaAntes.setDataAtualizacao(LocalDateTime.now());
        consultaUmDiaAntes.setDataCriacao(LocalDateTime.now());
        consultaUmDiaAntes.setEspecialidade("ESPECIALIDADE");
        consultaUmDiaAntes.setPrioridade(PrioridadeConsulta.ALTA);

        consultaRepository.salvar(consultaUmDiaAntes);

        Consulta consultaUmDiaDepois = new Consulta();
        consultaUmDiaDepois.setId(UUID.randomUUID());
        consultaUmDiaDepois.setDataHora(duasSemanasFuturo.plusDays(1));
        consultaUmDiaDepois.setPacienteCpf(pacienteCpf);
        consultaUmDiaDepois.setLocalConsulta("Consultório I");
        consultaUmDiaDepois.setStatus(StatusConsulta.AGENDADA);
        consultaUmDiaDepois.setCidade("CIDADE");
        consultaUmDiaDepois.setDataAtualizacao(LocalDateTime.now());
        consultaUmDiaDepois.setDataCriacao(LocalDateTime.now());
        consultaUmDiaDepois.setEspecialidade("ESPECIALIDADE");
        consultaUmDiaDepois.setPrioridade(PrioridadeConsulta.ALTA);

        consultaRepository.salvar(consultaUmDiaDepois);

        // Act
        useCase.executar();

        // Assert
        verify(notificacaoService, times(3)).enviarNotificacao(any());
    }

    private void assertDatasIguaisIgnorandoNanos(String dataEsperada, String dataAtual) {
        try {
            LocalDateTime dtEsperada = LocalDateTime.parse(dataEsperada);
            LocalDateTime dtAtual = LocalDateTime.parse(dataAtual);

            dtEsperada = dtEsperada.withNano(dtEsperada.getNano() / 1_000_000 * 1_000_000);
            dtAtual = dtAtual.withNano(dtAtual.getNano() / 1_000_000 * 1_000_000);

            assertEquals(dtEsperada, dtAtual, "As datas são diferentes quando comparadas com precisão de milissegundos");
        } catch (DateTimeParseException e) {
            String dataEsperadaSemNanos = dataEsperada.replaceAll("\\.[0-9]+", "");
            String dataAtualSemNanos = dataAtual.replaceAll("\\.[0-9]+", "");
            assertTrue(dataAtualSemNanos.startsWith(dataEsperadaSemNanos),
                    "As datas não correspondem: esperado=" + dataEsperadaSemNanos + ", atual=" + dataAtualSemNanos);
        }
    }

}