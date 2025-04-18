package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.application.dtos.PacienteDTO;
import com.fiap.consultas.application.ports.NotificacaoServicePort;
import com.fiap.consultas.application.ports.PacienteServicePort;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.enums.TipoNotificacao;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnviarNotificacaoConfirmacaoUseCaseTest {

    @Mock
    private ConsultaRepository consultaRepository;

    @Mock
    private PacienteServicePort pacienteServicePort;

    @Mock
    private NotificacaoServicePort notificacaoServicePort;

    @InjectMocks
    private EnviarNotificacaoConfirmacaoUseCase useCase;

    @Captor
    private ArgumentCaptor<NotificacaoDTO> notificacaoCaptor;

    private LocalDateTime dataAtual;
    private UUID consultaId;
    private String pacienteCpf;
    private String localConsulta;
    private PacienteDTO pacienteDTO;

    @BeforeEach
    void setUp() {
        dataAtual = LocalDateTime.now();
        consultaId = UUID.randomUUID();
        pacienteCpf = "12345678900";
        localConsulta = "Consultório A";

        // Criar PacienteDTO usando builder
        pacienteDTO = PacienteDTO.builder()
                .nome("João Silva")
                .email("joao@exemplo.com")
                .telefone("11999887766")
                .cpf(pacienteCpf)
                .build();
    }

    @Test
    void deveEnviarNotificacaoParaConsultasEm2Semanas() {
        // Arrange
        Consulta consultaAgendada = mock(Consulta.class);
        when(consultaAgendada.getId()).thenReturn(consultaId);
        when(consultaAgendada.getDataHora()).thenReturn(dataAtual.plusWeeks(2));
        when(consultaAgendada.getPacienteCpf()).thenReturn(pacienteCpf);
        when(consultaAgendada.getLocalConsulta()).thenReturn(localConsulta);

        when(consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA))
                .thenReturn(Collections.singletonList(consultaAgendada));
        when(pacienteServicePort.buscarPacientePorCpf(pacienteCpf))
                .thenReturn(pacienteDTO);

        // Act
        useCase.executar();

        // Assert
        verify(notificacaoServicePort).enviarNotificacao(notificacaoCaptor.capture());
        NotificacaoDTO notificacao = notificacaoCaptor.getValue();

        assertEquals(pacienteDTO.getNome(), notificacao.getNomePaciente());
        assertEquals(pacienteDTO.getEmail(), notificacao.getEmail());
        assertEquals(pacienteDTO.getTelefone(), notificacao.getTelefone());
        assertEquals(consultaId.toString(), notificacao.getConsulta());
        assertEquals(localConsulta, notificacao.getLocalConsulta());
        assertEquals(dataAtual.plusWeeks(2).toString(), notificacao.getDataConsulta());
        assertEquals(TipoNotificacao.CONFIRMACAO_CONSULTA, notificacao.getTipoNotificacao());
    }

    @Test
    void naoDeveEnviarNotificacaoParaConsultasAlemDe2Semanas() {
        // Arrange
        Consulta consultaAgendada = mock(Consulta.class);
        when(consultaAgendada.getDataHora()).thenReturn(dataAtual.plusWeeks(3));

        when(consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA))
                .thenReturn(Collections.singletonList(consultaAgendada));

        // Act
        useCase.executar();

        // Assert
        verify(notificacaoServicePort, never()).enviarNotificacao(any());
    }

    @Test
    void deveEnviarLembreteParaConsultasDeAmanha() {
        // Arrange
        Consulta consultaConfirmada = mock(Consulta.class);
        when(consultaConfirmada.getId()).thenReturn(consultaId);
        when(consultaConfirmada.getDataHora()).thenReturn(dataAtual.plusDays(1));
        when(consultaConfirmada.getPacienteCpf()).thenReturn(pacienteCpf);
        when(consultaConfirmada.getLocalConsulta()).thenReturn(localConsulta);

        when(consultaRepository.buscarPorStatus(StatusConsulta.CONFIRMADA))
                .thenReturn(Collections.singletonList(consultaConfirmada));
        when(pacienteServicePort.buscarPacientePorCpf(pacienteCpf))
                .thenReturn(pacienteDTO);

        // Act
        useCase.enviarLembreteDiaAnterior();

        // Assert
        verify(notificacaoServicePort).enviarNotificacao(notificacaoCaptor.capture());
        NotificacaoDTO notificacao = notificacaoCaptor.getValue();

        assertEquals(pacienteDTO.getNome(), notificacao.getNomePaciente());
        assertEquals(pacienteDTO.getEmail(), notificacao.getEmail());
        assertEquals(pacienteDTO.getTelefone(), notificacao.getTelefone());
        assertEquals(consultaId.toString(), notificacao.getConsulta());
        assertEquals(localConsulta, notificacao.getLocalConsulta());
        assertEquals(dataAtual.plusDays(1).toString(), notificacao.getDataConsulta());
        assertEquals(TipoNotificacao.AVISO_UM_DIA_ANTES, notificacao.getTipoNotificacao());
    }

    @Test
    void naoDeveEnviarLembreteParaConsultasNaoAgendadasParaAmanha() {
        // Arrange
        Consulta consultaOutroDia = mock(Consulta.class);
        when(consultaOutroDia.getDataHora()).thenReturn(dataAtual.plusDays(2)); // Daqui a 2 dias

        when(consultaRepository.buscarPorStatus(StatusConsulta.CONFIRMADA))
                .thenReturn(Collections.singletonList(consultaOutroDia));

        // Act
        useCase.enviarLembreteDiaAnterior();

        // Assert
        verify(notificacaoServicePort, never()).enviarNotificacao(any());
    }

    @Test
    void naoDeveEnviarNotificacaoQuandoNaoHaConsultasAgendadas() {
        // Arrange
        when(consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA))
                .thenReturn(Collections.emptyList());

        // Act
        useCase.executar();

        // Assert
        verify(notificacaoServicePort, never()).enviarNotificacao(any());
    }

    @Test
    void naoDeveEnviarLembreteQuandoNaoHaConsultasConfirmadas() {
        // Arrange
        when(consultaRepository.buscarPorStatus(StatusConsulta.CONFIRMADA))
                .thenReturn(Collections.emptyList());

        // Act
        useCase.enviarLembreteDiaAnterior();

        // Assert
        verify(notificacaoServicePort, never()).enviarNotificacao(any());
    }

    @Test
    void deveEnviarNotificacaoParaMultiplasConsultasProximas() {
        // Arrange
        Consulta consulta1 = mock(Consulta.class);
        when(consulta1.getId()).thenReturn(UUID.randomUUID());
        when(consulta1.getDataHora()).thenReturn(dataAtual.plusWeeks(2));
        when(consulta1.getPacienteCpf()).thenReturn("11111111111");
        when(consulta1.getLocalConsulta()).thenReturn("Consultório X");

        Consulta consulta2 = mock(Consulta.class);
        when(consulta2.getId()).thenReturn(UUID.randomUUID());
        when(consulta2.getDataHora()).thenReturn(dataAtual.plusWeeks(2).minusDays(1));
        when(consulta2.getPacienteCpf()).thenReturn("22222222222");
        when(consulta2.getLocalConsulta()).thenReturn("Consultório Y");

        when(consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA))
                .thenReturn(Arrays.asList(consulta1, consulta2));

        PacienteDTO paciente1 = PacienteDTO.builder()
                .nome("Paciente 1")
                .email("paciente1@exemplo.com")
                .telefone("11111111111")
                .cpf("11111111111")
                .build();

        PacienteDTO paciente2 = PacienteDTO.builder()
                .nome("Paciente 2")
                .email("paciente2@exemplo.com")
                .telefone("22222222222")
                .cpf("22222222222")
                .build();

        when(pacienteServicePort.buscarPacientePorCpf("11111111111")).thenReturn(paciente1);
        when(pacienteServicePort.buscarPacientePorCpf("22222222222")).thenReturn(paciente2);

        // Act
        useCase.executar();

        // Assert
        verify(notificacaoServicePort, times(2)).enviarNotificacao(any());
    }

    @Test
    void deveEnviarLembreteParaMultiplasConsultasDeAmanha() {
        // Arrange
        Consulta consulta1 = mock(Consulta.class);
        when(consulta1.getId()).thenReturn(UUID.randomUUID());
        when(consulta1.getDataHora()).thenReturn(dataAtual.plusDays(1).withHour(9));
        when(consulta1.getPacienteCpf()).thenReturn("11111111111");
        when(consulta1.getLocalConsulta()).thenReturn("Consultório X");

        Consulta consulta2 = mock(Consulta.class);
        when(consulta2.getId()).thenReturn(UUID.randomUUID());
        when(consulta2.getDataHora()).thenReturn(dataAtual.plusDays(1).withHour(14));
        when(consulta2.getPacienteCpf()).thenReturn("22222222222");
        when(consulta2.getLocalConsulta()).thenReturn("Consultório Y");

        when(consultaRepository.buscarPorStatus(StatusConsulta.CONFIRMADA))
                .thenReturn(Arrays.asList(consulta1, consulta2));

        PacienteDTO paciente1 = PacienteDTO.builder()
                .nome("Paciente 1")
                .email("paciente1@exemplo.com")
                .telefone("11111111111")
                .cpf("11111111111")
                .build();

        PacienteDTO paciente2 = PacienteDTO.builder()
                .nome("Paciente 2")
                .email("paciente2@exemplo.com")
                .telefone("22222222222")
                .cpf("22222222222")
                .build();

        when(pacienteServicePort.buscarPacientePorCpf("11111111111")).thenReturn(paciente1);
        when(pacienteServicePort.buscarPacientePorCpf("22222222222")).thenReturn(paciente2);

        // Act
        useCase.enviarLembreteDiaAnterior();

        // Assert
        verify(notificacaoServicePort, times(2)).enviarNotificacao(any());
    }

    @Test
    void testeMetodoEstaProximoDuasSemanas() {
        // Arrange
        LocalDateTime duasSemanasFuturo = LocalDateTime.now().plusWeeks(2);

        Consulta consultaExatamenteDuasSemanas = mock(Consulta.class);
        when(consultaExatamenteDuasSemanas.getId()).thenReturn(UUID.randomUUID());
        when(consultaExatamenteDuasSemanas.getDataHora()).thenReturn(duasSemanasFuturo);
        when(consultaExatamenteDuasSemanas.getPacienteCpf()).thenReturn("11111111111");
        when(consultaExatamenteDuasSemanas.getLocalConsulta()).thenReturn("Consultório X");

        Consulta consultaMenosDeDuasSemanas = mock(Consulta.class);
        when(consultaMenosDeDuasSemanas.getDataHora()).thenReturn(duasSemanasFuturo.minusDays(1));
        when(consultaMenosDeDuasSemanas.getId()).thenReturn(UUID.randomUUID());
        when(consultaMenosDeDuasSemanas.getPacienteCpf()).thenReturn("2222222");
        when(consultaMenosDeDuasSemanas.getLocalConsulta()).thenReturn("Consultório X");

        Consulta consultaMaisDeDuasSemanas = mock(Consulta.class);
        when(consultaMaisDeDuasSemanas.getDataHora()).thenReturn(duasSemanasFuturo.plusDays(1));

        when(consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA)).thenReturn(Arrays.asList(consultaExatamenteDuasSemanas, consultaMenosDeDuasSemanas, consultaMaisDeDuasSemanas));
        when(pacienteServicePort.buscarPacientePorCpf(any())).thenReturn(pacienteDTO);

        // Act
        useCase.executar();

        // Assert - deve enviar 2 notificações (exatamente 2 semanas e menos de 2 semanas)
        verify(notificacaoServicePort, times(2)).enviarNotificacao(any());
    }

    @Test
    void testeMetodoEhAmanha() {
        // Arrange
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime amanha = agora.plusDays(1);

        Consulta consultaAmanha = mock(Consulta.class);
        when(consultaAmanha.getId()).thenReturn(UUID.randomUUID());
        when(consultaAmanha.getDataHora()).thenReturn(amanha);
        when(consultaAmanha.getPacienteCpf()).thenReturn(pacienteCpf);
        when(consultaAmanha.getLocalConsulta()).thenReturn(localConsulta);

        Consulta consultaHoje = mock(Consulta.class);
        when(consultaHoje.getDataHora()).thenReturn(agora);

        Consulta consultaDepoisDeAmanha = mock(Consulta.class);
        when(consultaDepoisDeAmanha.getDataHora()).thenReturn(agora.plusDays(2));

        when(consultaRepository.buscarPorStatus(StatusConsulta.CONFIRMADA))
                .thenReturn(Arrays.asList(consultaAmanha, consultaHoje, consultaDepoisDeAmanha));
        when(pacienteServicePort.buscarPacientePorCpf(any())).thenReturn(pacienteDTO);

        // Act
        useCase.enviarLembreteDiaAnterior();

        // Assert - deve enviar apenas para consulta de amanhã
        verify(notificacaoServicePort, times(1)).enviarNotificacao(any());
    }
}