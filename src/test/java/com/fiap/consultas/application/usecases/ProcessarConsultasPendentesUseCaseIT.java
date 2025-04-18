package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.MedicoDTO;
import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.application.dtos.PacienteDTO;
import com.fiap.consultas.application.ports.MedicoServicePort;
import com.fiap.consultas.application.ports.NotificacaoServicePort;
import com.fiap.consultas.application.ports.PacienteServicePort;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.entities.HorarioTrabalho;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.enums.TipoNotificacao;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import com.fiap.consultas.domain.services.AgendamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureTestDatabase
@EnableTestBinder
class ProcessarConsultasPendentesUseCaseIT {

    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private AgendamentoService agendamentoService;

    @MockitoBean
    private PacienteServicePort pacienteServicePort;

    @MockitoBean
    private MedicoServicePort medicoServicePort;

    @MockitoBean
    private NotificacaoServicePort notificacaoServicePort;

    private ProcessarConsultasPendentesUseCase useCase;

    private List<NotificacaoDTO> notificacoesEnviadas;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        notificacoesEnviadas = new ArrayList<>();

        doAnswer(invocation -> {
            NotificacaoDTO notificacao = invocation.getArgument(0);
            notificacoesEnviadas.add(notificacao);
            return null;
        }).when(notificacaoServicePort).enviarNotificacao(any(NotificacaoDTO.class));

        useCase = new ProcessarConsultasPendentesUseCase(
                consultaRepository,
                pacienteServicePort,
                medicoServicePort,
                notificacaoServicePort,
                agendamentoService
        );
        jdbcTemplate.execute("DELETE FROM consultas");
    }

    private LocalDateTime ajustarHorarioConsulta(LocalDateTime dataHora) {
        int minuto = dataHora.getMinute();
        if (minuto < 30) {
            return dataHora.withMinute(0).withSecond(0).withNano(0);
        } else {
            return dataHora.withMinute(30).withSecond(0).withNano(0);
        }
    }

    @Test
    void processar_FluxoCompletoConsultaUrgente_DeveAgendarENotificar() {
        LocalDateTime agora = LocalDateTime.now();

        UUID consultaId = UUID.randomUUID();
        Consulta consultaUrgente = Consulta.builder()
                .id(consultaId)
                .pacienteCpf("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(agora.minusDays(1))
                .dataAtualizacao(agora.minusDays(1))
                .build();

        consultaRepository.salvar(consultaUrgente);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        LocalDateTime amanha = ajustarHorarioConsulta(agora.plusDays(1));
        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Teste")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(amanha.getDayOfWeek())
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo"))
                .thenReturn(List.of(medicoDTO));

        useCase.executar();


        Consulta consultaAtualizada = consultaRepository.buscarPorId(consultaId)
                .orElseThrow(() -> new AssertionError("Consulta não encontrada após processamento"));
        assertEquals(StatusConsulta.AGENDADA, consultaAtualizada.getStatus());
        assertEquals("med-123", consultaAtualizada.getMedicoId());
        assertNotNull(consultaAtualizada.getDataHora());
        assertTrue(consultaAtualizada.getDataHora().isAfter(agora));
        assertNotNull(consultaAtualizada.getLocalConsulta());

        verify(notificacaoServicePort, times(1)).enviarNotificacao(any(NotificacaoDTO.class));

        assertEquals(1, notificacoesEnviadas.size());
        NotificacaoDTO notificacao = notificacoesEnviadas.getFirst();
        assertEquals("Paciente Teste", notificacao.getNomePaciente());
        assertEquals("paciente@teste.com", notificacao.getEmail());
        assertEquals(TipoNotificacao.CONSULTA_AGENDADA, notificacao.getTipoNotificacao());
        assertEquals(consultaId.toString(), notificacao.getConsulta());
    }

    @Test
    void processar_FluxoCompletoRemanejamento_DeveReagendarConsultas() {
        LocalDateTime agora = LocalDateTime.now();

        UUID consultaUrgenteId = UUID.randomUUID();
        Consulta consultaUrgente = Consulta.builder()
                .id(consultaUrgenteId)
                .pacienteCpf("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.URGENTE)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(agora.minusDays(1))
                .dataAtualizacao(agora.minusDays(1))
                .build();

        UUID consultaNormalId = UUID.randomUUID();
        LocalDateTime dataHoraConsultaNormal = ajustarHorarioConsulta(agora.plusDays(2).withHour(8));
        Consulta consultaNormal = Consulta.builder()
                .id(consultaNormalId)
                .pacienteCpf("98765432100")
                .medicoId("med-123")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .dataHora(dataHoraConsultaNormal)
                .localConsulta("Consultório Dr. Teste")
                .prioridade(PrioridadeConsulta.BAIXA)
                .status(StatusConsulta.AGENDADA)
                .dataCriacao(agora.minusDays(5))
                .dataAtualizacao(agora.minusDays(5))
                .build();

        consultaRepository.salvar(consultaUrgente);
        consultaRepository.salvar(consultaNormal);

        PacienteDTO pacienteUrgenteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Urgente")
                .email("urgente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        PacienteDTO pacienteNormalDTO = PacienteDTO.builder()
                .cpf("98765432100")
                .nome("Paciente Normal")
                .email("normal@teste.com")
                .telefone("11987654322")
                .cidade("São Paulo")
                .build();

        LocalDateTime diaFuturo = ajustarHorarioConsulta(agora.plusDays(5));
        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Teste")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(dataHoraConsultaNormal.getDayOfWeek())
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build(),
                        HorarioTrabalho.builder()
                                .diaSemana(diaFuturo.getDayOfWeek())
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteUrgenteDTO);
        when(pacienteServicePort.buscarPacientePorCpf("98765432100")).thenReturn(pacienteNormalDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo"))
                .thenReturn(List.of(medicoDTO));

        useCase.executar();

        Consulta consultaUrgenteAtualizada = consultaRepository.buscarPorId(consultaUrgenteId)
                .orElseThrow(() -> new AssertionError("Consulta urgente não encontrada após processamento"));

        Consulta consultaNormalAtualizada = consultaRepository.buscarPorId(consultaNormalId)
                .orElseThrow(() -> new AssertionError("Consulta normal não encontrada após processamento"));

        assertEquals(StatusConsulta.AGENDADA, consultaUrgenteAtualizada.getStatus());
        assertEquals("med-123", consultaUrgenteAtualizada.getMedicoId());
        assertEquals(dataHoraConsultaNormal, consultaUrgenteAtualizada.getDataHora());

        assertNotEquals(dataHoraConsultaNormal, consultaNormalAtualizada.getDataHora());
        assertTrue(consultaNormalAtualizada.getDataHora().isAfter(dataHoraConsultaNormal));

        verify(notificacaoServicePort, times(2)).enviarNotificacao(any(NotificacaoDTO.class));
        assertEquals(2, notificacoesEnviadas.size());
    }

    @Test
    void processar_QuandoNaoHaMedicosDisponiveis_DeveNotificarListaEspera() {
        LocalDateTime agora = LocalDateTime.now();

        UUID consultaId = UUID.randomUUID();
        Consulta consultaPendente = Consulta.builder()
                .id(consultaId)
                .pacienteCpf("12345678900")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(agora.minusDays(1))
                .dataAtualizacao(agora.minusDays(1))
                .build();

        consultaRepository.salvar(consultaPendente);

        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo"))
                .thenReturn(List.of());

        useCase.executar();

        Consulta consultaAtualizada = consultaRepository.buscarPorId(consultaId)
                .orElseThrow(() -> new AssertionError("Consulta não encontrada após processamento"));

        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, consultaAtualizada.getStatus());
        assertNull(consultaAtualizada.getMedicoId());
        assertNull(consultaAtualizada.getDataHora());

        verify(notificacaoServicePort).enviarNotificacao(any(NotificacaoDTO.class));
        assertEquals(1, notificacoesEnviadas.size());

        NotificacaoDTO notificacao = notificacoesEnviadas.getFirst();
        assertEquals("Paciente Teste", notificacao.getNomePaciente());
        assertEquals("paciente@teste.com", notificacao.getEmail());
        assertEquals(TipoNotificacao.ENTRADA_LISTA_ESPERA, notificacao.getTipoNotificacao());
    }

    @Test
    void processar_MedicoComMenosCarga_DeveDistribuirConsultasIgualmente() {
        LocalDateTime agora = LocalDateTime.now();

        UUID consulta1Id = UUID.randomUUID();
        Consulta consulta1 = Consulta.builder()
                .id(consulta1Id)
                .pacienteCpf("11111111111")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(agora.minusHours(3))
                .dataAtualizacao(agora.minusHours(3))
                .build();

        UUID consulta2Id = UUID.randomUUID();
        Consulta consulta2 = Consulta.builder()
                .id(consulta2Id)
                .pacienteCpf("22222222222")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(agora.minusHours(2))
                .dataAtualizacao(agora.minusHours(2))
                .build();

        UUID consultaAgendadaId = UUID.randomUUID();
        LocalDateTime amanha = ajustarHorarioConsulta(agora.plusDays(1).withHour(10));
        Consulta consultaAgendada = Consulta.builder()
                .id(consultaAgendadaId)
                .pacienteCpf("33333333333")
                .medicoId("med-123") // Já tem uma consulta para este médico
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .dataHora(amanha)
                .localConsulta("Consultório Dr. Ocupado")
                .prioridade(PrioridadeConsulta.BAIXA)
                .status(StatusConsulta.AGENDADA)
                .dataCriacao(agora.minusDays(1))
                .dataAtualizacao(agora.minusDays(1))
                .build();

        consultaRepository.salvar(consulta1);
        consultaRepository.salvar(consulta2);
        consultaRepository.salvar(consultaAgendada);

        PacienteDTO paciente1DTO = PacienteDTO.builder()
                .cpf("11111111111")
                .nome("Paciente 1")
                .email("paciente1@teste.com")
                .telefone("11111111111")
                .cidade("São Paulo")
                .build();

        PacienteDTO paciente2DTO = PacienteDTO.builder()
                .cpf("22222222222")
                .nome("Paciente 2")
                .email("paciente2@teste.com")
                .telefone("22222222222")
                .cidade("São Paulo")
                .build();

        MedicoDTO medico1DTO = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Ocupado")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(amanha.getDayOfWeek())
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        MedicoDTO medico2DTO = MedicoDTO.builder()
                .id("med-456")
                .nome("Dra. Disponível")
                .especialidade("DERMATOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(amanha.getDayOfWeek())
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        when(pacienteServicePort.buscarPacientePorCpf("11111111111")).thenReturn(paciente1DTO);
        when(pacienteServicePort.buscarPacientePorCpf("22222222222")).thenReturn(paciente2DTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("DERMATOLOGIA", "São Paulo"))
                .thenReturn(Arrays.asList(medico1DTO, medico2DTO));

        useCase.executar();

        Consulta consulta1Atualizada = consultaRepository.buscarPorId(consulta1Id)
                .orElseThrow(() -> new AssertionError("Consulta 1 não encontrada após processamento"));

        Consulta consulta2Atualizada = consultaRepository.buscarPorId(consulta2Id)
                .orElseThrow(() -> new AssertionError("Consulta 2 não encontrada após processamento"));

        assertNotEquals(consulta1Atualizada.getMedicoId(), consulta2Atualizada.getMedicoId(),
                "As consultas deveriam ser distribuídas entre médicos diferentes");

        boolean peloMenosUmaComMedico2 =
                "med-456".equals(consulta1Atualizada.getMedicoId()) ||
                        "med-456".equals(consulta2Atualizada.getMedicoId());

        assertTrue(peloMenosUmaComMedico2, "Pelo menos uma consulta deveria ser agendada com o médico menos ocupado");

        verify(notificacaoServicePort, times(2)).enviarNotificacao(any(NotificacaoDTO.class));
        assertEquals(2, notificacoesEnviadas.size());
    }
}