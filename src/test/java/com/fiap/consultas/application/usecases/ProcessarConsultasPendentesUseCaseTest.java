package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.MedicoDTO;
import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.application.dtos.PacienteDTO;
import com.fiap.consultas.application.ports.MedicoServicePort;
import com.fiap.consultas.application.ports.NotificacaoServicePort;
import com.fiap.consultas.application.ports.PacienteServicePort;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.entities.HorarioTrabalho;
import com.fiap.consultas.domain.entities.Medico;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.enums.TipoNotificacao;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import com.fiap.consultas.domain.services.AgendamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessarConsultasPendentesUseCaseTest {

    @Mock
    private ConsultaRepository consultaRepository;

    @Mock
    private PacienteServicePort pacienteServicePort;

    @Mock
    private MedicoServicePort medicoServicePort;

    @Mock
    private NotificacaoServicePort notificacaoServicePort;

    @Mock
    private AgendamentoService agendamentoService;

    private ProcessarConsultasPendentesUseCase useCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new ProcessarConsultasPendentesUseCase(
                consultaRepository,
                pacienteServicePort,
                medicoServicePort,
                notificacaoServicePort,
                agendamentoService
        );
    }

    @Test
    void executar_quandoNaoHaConsultasPendentes_NaoDeveProcessar() {
        // Given
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(Collections.emptyList());

        // Execute
        useCase.executar();

        // Then
        verify(pacienteServicePort, never()).buscarPacientePorCpf(anyString());
        verify(medicoServicePort, never()).buscarMedicosPorEspecialidadeECidade(anyString(), anyString());
        verify(notificacaoServicePort, never()).enviarNotificacao(any());
        verify(consultaRepository, never()).salvar(any());
    }

    @Test
    void executar_quandoConsultaUrgente_EEncontraHorarioVago_DeveAgendar() {
        // Given
        UUID consultaId = UUID.randomUUID();
        Consulta consultaUrgente = Consulta.builder()
                .id(consultaId)
                .pacienteCpf("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .build();

        List<Consulta> consultasPendentes = Collections.singletonList(consultaUrgente);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Teste")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.MONDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        LocalDateTime horarioVago = LocalDateTime.now().plusDays(1);

        // When
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo")).thenReturn(List.of(medicoDTO));
        when(agendamentoService.encontrarProximoHorarioDisponivel(anyList(), eq("CARDIOLOGIA"), eq("São Paulo"))).thenReturn(horarioVago);
        when(agendamentoService.buscarConsultasParaReagendar("CARDIOLOGIA", "São Paulo", PrioridadeConsulta.URGENTE)).thenReturn(Collections.emptyList());
        when(agendamentoService.isHorarioDisponivel(any(Medico.class), eq(horarioVago))).thenReturn(true);
        when(consultaRepository.buscarConsultasPorMedicoEIntervalo(eq("med-123"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Execute
        useCase.executar();

        // Then
        ArgumentCaptor<Consulta> consultaCaptor = ArgumentCaptor.forClass(Consulta.class);
        verify(consultaRepository).salvar(consultaCaptor.capture());

        Consulta consultaSalva = consultaCaptor.getValue();
        assertEquals(consultaId, consultaSalva.getId());
        assertEquals(StatusConsulta.AGENDADA, consultaSalva.getStatus());
        assertEquals("med-123", consultaSalva.getMedicoId());
        assertEquals(horarioVago, consultaSalva.getDataHora());
        assertEquals("Consultório Dr. Teste", consultaSalva.getLocalConsulta());

        ArgumentCaptor<NotificacaoDTO> notificacaoCaptor = ArgumentCaptor.forClass(NotificacaoDTO.class);
        verify(notificacaoServicePort).enviarNotificacao(notificacaoCaptor.capture());

        NotificacaoDTO notificacao = notificacaoCaptor.getValue();
        assertEquals("Paciente Teste", notificacao.getNomePaciente());
        assertEquals("paciente@teste.com", notificacao.getEmail());
        assertEquals(TipoNotificacao.CONSULTA_AGENDADA, notificacao.getTipoNotificacao());
    }

    @Test
    void executar_quandoConsultaUrgente_ENaoEncontraMedicos_DeveNotificarListaEspera() {
        // Given
        UUID consultaId = UUID.randomUUID();
        Consulta consultaUrgente = Consulta.builder()
                .id(consultaId)
                .pacienteCpf("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .build();

        List<Consulta> consultasPendentes = Collections.singletonList(consultaUrgente);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        // When
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo")).thenReturn(Collections.emptyList());

        // Execute
        useCase.executar();

        // Then
        verify(consultaRepository, never()).salvar(any(Consulta.class));

        ArgumentCaptor<NotificacaoDTO> notificacaoCaptor = ArgumentCaptor.forClass(NotificacaoDTO.class);
        verify(notificacaoServicePort).enviarNotificacao(notificacaoCaptor.capture());

        NotificacaoDTO notificacao = notificacaoCaptor.getValue();
        assertEquals("Paciente Teste", notificacao.getNomePaciente());
        assertEquals("paciente@teste.com", notificacao.getEmail());
        assertEquals(TipoNotificacao.ENTRADA_LISTA_ESPERA, notificacao.getTipoNotificacao());
    }

    @Test
    void executar_quandoConsultaUrgente_EEncontraConsultaParaRemarcar_DeveRemanejar() {
        // Given
        UUID consultaUrgenteId = UUID.randomUUID();
        Consulta consultaUrgente = Consulta.builder()
                .id(consultaUrgenteId)
                .pacienteCpf("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .build();

        UUID consultaParaRemanejarId = UUID.randomUUID();
        LocalDateTime dataHoraConsultaExistente = LocalDateTime.now().plusDays(2);
        Consulta consultaParaRemanejar = Consulta.builder()
                .id(consultaParaRemanejarId)
                .pacienteCpf("98765432100")
                .especialidade("CARDIOLOGIA")
                .medicoId("med-123")
                .cidade("São Paulo")
                .dataHora(dataHoraConsultaExistente)
                .localConsulta("Consultório Dr. Teste")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.AGENDADA)
                .dataCriacao(LocalDateTime.now().minusDays(3))
                .build();

        List<Consulta> consultasPendentes = Collections.singletonList(consultaUrgente);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        PacienteDTO pacienteDTORemanejado = PacienteDTO.builder()
                .cpf("98765432100")
                .nome("Paciente Remanejado")
                .email("remanejado@teste.com")
                .telefone("11987654322")
                .cidade("São Paulo")
                .build();

        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Teste")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.MONDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        LocalDateTime novoHorarioParaRemanejar = LocalDateTime.now().plusDays(4);

        // When
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(pacienteServicePort.buscarPacientePorCpf("98765432100")).thenReturn(pacienteDTORemanejado);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo")).thenReturn(List.of(medicoDTO));
        when(agendamentoService.encontrarProximoHorarioDisponivel(anyList(), eq("CARDIOLOGIA"), eq("São Paulo"))).thenReturn(novoHorarioParaRemanejar);
        when(agendamentoService.buscarConsultasParaReagendar("CARDIOLOGIA", "São Paulo", PrioridadeConsulta.URGENTE)).thenReturn(List.of(consultaParaRemanejar));

        // Execute
        useCase.executar();

        // Then
        ArgumentCaptor<Consulta> consultaCaptor = ArgumentCaptor.forClass(Consulta.class);
        verify(consultaRepository, times(2)).salvar(consultaCaptor.capture());

        List<Consulta> consultasSalvas = consultaCaptor.getAllValues();
        Consulta consultaUrgenteSalva = consultasSalvas.get(0);
        Consulta consultaRemanejadaSalva = consultasSalvas.get(1);

        // Verificações para a consulta urgente
        assertEquals(consultaUrgenteId, consultaUrgenteSalva.getId());
        assertEquals(StatusConsulta.AGENDADA, consultaUrgenteSalva.getStatus());
        assertEquals("med-123", consultaUrgenteSalva.getMedicoId());
        assertEquals(dataHoraConsultaExistente, consultaUrgenteSalva.getDataHora());
        assertEquals("Consultório Dr. Teste", consultaUrgenteSalva.getLocalConsulta());

        // Verificações para a consulta remanejada
        assertEquals(consultaParaRemanejarId, consultaRemanejadaSalva.getId());
        assertEquals(StatusConsulta.AGENDADA, consultaRemanejadaSalva.getStatus());
        assertEquals(novoHorarioParaRemanejar, consultaRemanejadaSalva.getDataHora());

        // Verificações de notificações
        verify(notificacaoServicePort, times(2)).enviarNotificacao(any(NotificacaoDTO.class));
    }

    @Test
    void executar_quandoConsultaNormal_EEncontraHorario_DeveAgendar() {
        // Given
        UUID consultaId = UUID.randomUUID();
        Consulta consultaNormal = Consulta.builder()
                .id(consultaId)
                .pacienteCpf("12345678900")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .build();

        List<Consulta> consultasPendentes = Collections.singletonList(consultaNormal);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-456")
                .nome("Dra. Especialista")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.TUESDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        LocalDateTime horarioVago = LocalDateTime.now().plusDays(3);

        // When
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("DERMATOLOGIA", "São Paulo")).thenReturn(List.of(medicoDTO));
        when(agendamentoService.encontrarProximoHorarioDisponivel(anyList(), eq("DERMATOLOGIA"), eq("São Paulo"))).thenReturn(horarioVago);
        when(agendamentoService.isHorarioDisponivel(any(Medico.class), eq(horarioVago))).thenReturn(true);

        // Execute
        useCase.executar();

        // Then
        ArgumentCaptor<Consulta> consultaCaptor = ArgumentCaptor.forClass(Consulta.class);
        verify(consultaRepository).salvar(consultaCaptor.capture());

        Consulta consultaSalva = consultaCaptor.getValue();
        assertEquals(consultaId, consultaSalva.getId());
        assertEquals(StatusConsulta.AGENDADA, consultaSalva.getStatus());
        assertEquals("med-456", consultaSalva.getMedicoId());
        assertEquals(horarioVago, consultaSalva.getDataHora());
        assertEquals("Consultório Dra. Especialista", consultaSalva.getLocalConsulta());

        ArgumentCaptor<NotificacaoDTO> notificacaoCaptor = ArgumentCaptor.forClass(NotificacaoDTO.class);
        verify(notificacaoServicePort).enviarNotificacao(notificacaoCaptor.capture());

        NotificacaoDTO notificacao = notificacaoCaptor.getValue();
        assertEquals("Paciente Teste", notificacao.getNomePaciente());
        assertEquals("paciente@teste.com", notificacao.getEmail());
        assertEquals(TipoNotificacao.CONSULTA_AGENDADA, notificacao.getTipoNotificacao());
    }

    @Test
    void executar_quandoConsultaNormal_ENaoEncontraHorario_DeveNotificarListaEspera() {
        // Given
        UUID consultaId = UUID.randomUUID();
        Consulta consultaNormal = Consulta.builder()
                .id(consultaId)
                .pacienteCpf("12345678900")
                .especialidade("OFTALMOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.BAIXA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .build();

        List<Consulta> consultasPendentes = Collections.singletonList(consultaNormal);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-789")
                .nome("Dr. Oftalmologista")
                .especialidade("OFTALMOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.FRIDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        // When
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("OFTALMOLOGIA", "São Paulo")).thenReturn(List.of(medicoDTO));
        when(agendamentoService.encontrarProximoHorarioDisponivel(anyList(), eq("OFTALMOLOGIA"), eq("São Paulo"))).thenReturn(null);

        // Execute
        useCase.executar();

        // Then
        verify(consultaRepository, never()).salvar(any(Consulta.class));

        ArgumentCaptor<NotificacaoDTO> notificacaoCaptor = ArgumentCaptor.forClass(NotificacaoDTO.class);
        verify(notificacaoServicePort).enviarNotificacao(notificacaoCaptor.capture());

        NotificacaoDTO notificacao = notificacaoCaptor.getValue();
        assertEquals("Paciente Teste", notificacao.getNomePaciente());
        assertEquals("paciente@teste.com", notificacao.getEmail());
        assertEquals(TipoNotificacao.ENTRADA_LISTA_ESPERA, notificacao.getTipoNotificacao());
    }

    @Test
    void executar_quandoMaisDeUmMedico_DeveEscolherMedicoComMenosCarga() {
        // Given
        UUID consultaId = UUID.randomUUID();
        Consulta consultaNormal = Consulta.builder()
                .id(consultaId)
                .pacienteCpf("12345678900")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .build();

        List<Consulta> consultasPendentes = Collections.singletonList(consultaNormal);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        MedicoDTO medicoDTO1 = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Ocupado")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.MONDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        MedicoDTO medicoDTO2 = MedicoDTO.builder()
                .id("med-456")
                .nome("Dra. Disponível")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.MONDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        LocalDateTime horarioVago = LocalDateTime.now().plusDays(1);

        // Lista de 3 consultas para o médico "ocupado"
        List<Consulta> consultasMedico1 = Arrays.asList(
                Consulta.builder().id(UUID.randomUUID()).build(),
                Consulta.builder().id(UUID.randomUUID()).build(),
                Consulta.builder().id(UUID.randomUUID()).build()
        );

        // Lista vazia para o médico "disponível"
        List<Consulta> consultasMedico2 = Collections.emptyList();

        // When
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("DERMATOLOGIA", "São Paulo")).thenReturn(List.of(medicoDTO1, medicoDTO2));
        when(agendamentoService.encontrarProximoHorarioDisponivel(anyList(), eq("DERMATOLOGIA"), eq("São Paulo"))).thenReturn(horarioVago);
        when(agendamentoService.isHorarioDisponivel(any(Medico.class), eq(horarioVago))).thenReturn(true);
        when(consultaRepository.buscarConsultasPorMedicoEIntervalo(eq("med-123"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(consultasMedico1);
        when(consultaRepository.buscarConsultasPorMedicoEIntervalo(eq("med-456"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(consultasMedico2);

        // Execute
        useCase.executar();

        // Then
        ArgumentCaptor<Consulta> consultaCaptor = ArgumentCaptor.forClass(Consulta.class);
        verify(consultaRepository).salvar(consultaCaptor.capture());

        Consulta consultaSalva = consultaCaptor.getValue();
        assertEquals(consultaId, consultaSalva.getId());
        assertEquals("med-456", consultaSalva.getMedicoId(), "Deve escolher o médico com menos consultas no dia");
        assertEquals("Consultório Dra. Disponível", consultaSalva.getLocalConsulta());
    }

    @Test
    void executar_quandoConsultaUrgente_ENaoEncontraHorarioParaRemanejar_DeveColocarEmListaEspera() {
        // Given
        UUID consultaUrgenteId = UUID.randomUUID();
        Consulta consultaUrgente = Consulta.builder()
                .id(consultaUrgenteId)
                .pacienteCpf("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .build();

        UUID consultaParaRemanejarId = UUID.randomUUID();
        LocalDateTime dataHoraConsultaExistente = LocalDateTime.now().plusDays(2);
        Consulta consultaParaRemanejar = Consulta.builder()
                .id(consultaParaRemanejarId)
                .pacienteCpf("98765432100")
                .especialidade("CARDIOLOGIA")
                .medicoId("med-123")
                .cidade("São Paulo")
                .dataHora(dataHoraConsultaExistente)
                .localConsulta("Consultório Dr. Teste")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.AGENDADA)
                .dataCriacao(LocalDateTime.now().minusDays(3))
                .build();

        List<Consulta> consultasPendentes = Collections.singletonList(consultaUrgente);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        PacienteDTO pacienteDTORemanejado = PacienteDTO.builder()
                .cpf("98765432100")
                .nome("Paciente Remanejado")
                .email("remanejado@teste.com")
                .telefone("11987654322")
                .cidade("São Paulo")
                .build();

        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Teste")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.MONDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        // When
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(pacienteServicePort.buscarPacientePorCpf("98765432100")).thenReturn(pacienteDTORemanejado);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo")).thenReturn(List.of(medicoDTO));
        when(agendamentoService.encontrarProximoHorarioDisponivel(anyList(), eq("CARDIOLOGIA"), eq("São Paulo"))).thenReturn(null);
        when(agendamentoService.buscarConsultasParaReagendar("CARDIOLOGIA", "São Paulo", PrioridadeConsulta.URGENTE)).thenReturn(List.of(consultaParaRemanejar));

        // Execute
        useCase.executar();

        // Then
        ArgumentCaptor<Consulta> consultaCaptor = ArgumentCaptor.forClass(Consulta.class);
        verify(consultaRepository, times(2)).salvar(consultaCaptor.capture());

        List<Consulta> consultasSalvas = consultaCaptor.getAllValues();
        Consulta consultaUrgenteSalva = consultasSalvas.get(0);
        Consulta consultaRemanejadaSalva = consultasSalvas.get(1);

        // Verificações para a consulta urgente
        assertEquals(consultaUrgenteId, consultaUrgenteSalva.getId());
        assertEquals(StatusConsulta.AGENDADA, consultaUrgenteSalva.getStatus());
        assertEquals("med-123", consultaUrgenteSalva.getMedicoId());
        assertEquals(dataHoraConsultaExistente, consultaUrgenteSalva.getDataHora());

        // Verificações para a consulta remanejada
        assertEquals(consultaParaRemanejarId, consultaRemanejadaSalva.getId());
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, consultaRemanejadaSalva.getStatus());

        // Verifica que duas notificações foram enviadas
        verify(notificacaoServicePort, times(2)).enviarNotificacao(any(NotificacaoDTO.class));
    }

    @Test
    void executar_quandoOcorreExcecao_DeveContinuarProcessando() {
        // Given
        UUID consultaId1 = UUID.randomUUID();
        Consulta consulta1 = Consulta.builder()
                .id(consultaId1)
                .pacienteCpf("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1))
                .build();

        UUID consultaId2 = UUID.randomUUID();
        Consulta consulta2 = Consulta.builder()
                .id(consultaId2)
                .pacienteCpf("98765432100")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(2))
                .build();

        List<Consulta> consultasPendentes = Arrays.asList(consulta1, consulta2);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("98765432100")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-456")
                .nome("Dra. Especialista")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.TUESDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        LocalDateTime horarioVago = LocalDateTime.now().plusDays(3);

        // When - primeiro paciente causa exceção, segundo deve ser processado normalmente
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenThrow(new RuntimeException("Erro ao buscar paciente"));
        when(pacienteServicePort.buscarPacientePorCpf("98765432100")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("DERMATOLOGIA", "São Paulo")).thenReturn(List.of(medicoDTO));
        when(agendamentoService.encontrarProximoHorarioDisponivel(anyList(), eq("DERMATOLOGIA"), eq("São Paulo"))).thenReturn(horarioVago);
        when(agendamentoService.isHorarioDisponivel(any(Medico.class), eq(horarioVago))).thenReturn(true);

        // Execute
        useCase.executar();

        // Then - a exceção não deve impedir de processar a segunda consulta
        ArgumentCaptor<Consulta> consultaCaptor = ArgumentCaptor.forClass(Consulta.class);
        verify(consultaRepository).salvar(consultaCaptor.capture());

        Consulta consultaSalva = consultaCaptor.getValue();
        assertEquals(consultaId2, consultaSalva.getId());
        assertEquals(StatusConsulta.AGENDADA, consultaSalva.getStatus());
    }

    @Test
    void executar_quandoOrdenandoConsultas_DeveOrdenarPorPrioridadeEData() {
        // Given
        UUID consultaIdUrgente = UUID.randomUUID();
        Consulta consultaUrgente = Consulta.builder()
                .id(consultaIdUrgente)
                .pacienteCpf("11111111111")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(1)) // Criada ontem
                .build();

        UUID consultaIdUrgenteMaisAntiga = UUID.randomUUID();
        Consulta consultaUrgenteMaisAntiga = Consulta.builder()
                .id(consultaIdUrgenteMaisAntiga)
                .pacienteCpf("22222222222")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(3)) // Criada 3 dias atrás
                .build();

        UUID consultaIdNormal = UUID.randomUUID();
        Consulta consultaNormal = Consulta.builder()
                .id(consultaIdNormal)
                .pacienteCpf("33333333333")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(LocalDateTime.now().minusDays(2)) // Criada 2 dias atrás
                .build();

        // Lista intencionalmente fora de ordem
        List<Consulta> consultasPendentes = Arrays.asList(consultaNormal, consultaUrgente, consultaUrgenteMaisAntiga);

        // Preparação de pacientes para os testes
        PacienteDTO pacienteDTO1 = PacienteDTO.builder()
                .cpf("11111111111")
                .nome("Paciente 1")
                .email("paciente1@teste.com")
                .telefone("11111111111")
                .cidade("São Paulo")
                .build();

        PacienteDTO pacienteDTO2 = PacienteDTO.builder()
                .cpf("22222222222")
                .nome("Paciente 2")
                .email("paciente2@teste.com")
                .telefone("22222222222")
                .cidade("São Paulo")
                .build();

        PacienteDTO pacienteDTO3 = PacienteDTO.builder()
                .cpf("33333333333")
                .nome("Paciente 3")
                .email("paciente3@teste.com")
                .telefone("33333333333")
                .cidade("São Paulo")
                .build();

        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Teste")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(DayOfWeek.MONDAY)
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        // When - configurando mocks para verificar a ordem de processamento
        when(consultaRepository.buscarConsultasPendentesAgendamento()).thenReturn(consultasPendentes);

        // Configurando o mock para lançar exceções diferentes conforme a ordem de chamada
        // para identificarmos a ordem de processamento
        when(pacienteServicePort.buscarPacientePorCpf("22222222222"))
                .thenReturn(pacienteDTO2)
                .thenThrow(new RuntimeException("Paciente 2 já foi processado"));

        when(pacienteServicePort.buscarPacientePorCpf("11111111111"))
                .thenReturn(pacienteDTO1)
                .thenThrow(new RuntimeException("Paciente 1 já foi processado"));

        when(pacienteServicePort.buscarPacientePorCpf("33333333333"))
                .thenReturn(pacienteDTO3)
                .thenThrow(new RuntimeException("Paciente 3 já foi processado"));

        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade(eq("CARDIOLOGIA"), eq("São Paulo")))
                .thenReturn(List.of(medicoDTO));

        when(agendamentoService.encontrarProximoHorarioDisponivel(anyList(), eq("CARDIOLOGIA"), eq("São Paulo")))
                .thenReturn(null); // Sem horários disponíveis para simplificar o teste

        // Execute
        useCase.executar();

        // Then - verificar a ordem de processamento através da ordem de chamadas
        // A ordem esperada é: consultaUrgenteMaisAntiga, consultaUrgente, consultaNormal
        // (primeiro por prioridade URGENTE, depois pela data mais antiga)

        // Verificando ordem de processamento
        InOrder inOrder = inOrder(pacienteServicePort);
        inOrder.verify(pacienteServicePort).buscarPacientePorCpf("22222222222"); // Paciente da consulta urgente mais antiga
        inOrder.verify(pacienteServicePort).buscarPacientePorCpf("11111111111"); // Paciente da consulta urgente
        inOrder.verify(pacienteServicePort).buscarPacientePorCpf("33333333333"); // Paciente da consulta normal
    }
}