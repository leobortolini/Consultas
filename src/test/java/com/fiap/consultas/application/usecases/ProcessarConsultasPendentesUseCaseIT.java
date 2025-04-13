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
        // Configurar lista para capturar notificações
        notificacoesEnviadas = new ArrayList<>();

        // Configurar mock de notificação para armazenar notificações enviadas
        doAnswer(invocation -> {
            NotificacaoDTO notificacao = invocation.getArgument(0);
            notificacoesEnviadas.add(notificacao);
            return null;
        }).when(notificacaoServicePort).enviarNotificacao(any(NotificacaoDTO.class));

        // Usar a implementação real do UseCase com os componentes injetados
        useCase = new ProcessarConsultasPendentesUseCase(
                consultaRepository,
                pacienteServicePort,
                medicoServicePort,
                notificacaoServicePort,
                agendamentoService
        );
        jdbcTemplate.execute("DELETE FROM consultas");
    }

    /**
     * Ajusta o horário para garantir que seja sempre em incrementos de 30 minutos (XX:00 ou XX:30)
     */
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
        // Given - Configurar dados e stubs
        LocalDateTime agora = LocalDateTime.now();

        // 1. Criar consulta pendente
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

        // 2. Salvar no repositório
        consultaRepository.salvar(consultaUrgente);

        // 3. Configurar dados do paciente
        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        // 4. Configurar dados do médico
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

        // 5. Configurar stubs
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo"))
                .thenReturn(List.of(medicoDTO));

        // Execute
        useCase.executar();

        // Verify - Verificar resultado

        // 1. Buscar a consulta após processamento
        Consulta consultaAtualizada = consultaRepository.buscarPorId(consultaId)
                .orElseThrow(() -> new AssertionError("Consulta não encontrada após processamento"));

        // 2. Verificar se foi agendada corretamente
        assertEquals(StatusConsulta.AGENDADA, consultaAtualizada.getStatus());
        assertEquals("med-123", consultaAtualizada.getMedicoId());
        assertNotNull(consultaAtualizada.getDataHora());
        assertTrue(consultaAtualizada.getDataHora().isAfter(agora));
        assertNotNull(consultaAtualizada.getLocalConsulta());

        // 3. Verificar se a data está em incrementos de 30 minutos
        int minutos = consultaAtualizada.getDataHora().getMinute();
        assertTrue(minutos == 0 || minutos == 30,
                "O minuto da consulta deve ser 0 ou 30, foi: " + minutos);

        // 4. Verificar se a notificação foi enviada
        verify(notificacaoServicePort, times(1)).enviarNotificacao(any(NotificacaoDTO.class));

        // 5. Verificar detalhes da notificação
        assertEquals(1, notificacoesEnviadas.size());
        NotificacaoDTO notificacao = notificacoesEnviadas.get(0);
        assertEquals("Paciente Teste", notificacao.getNomePaciente());
        assertEquals("paciente@teste.com", notificacao.getEmail());
        assertEquals(TipoNotificacao.CONSULTA_AGENDADA, notificacao.getTipoNotificacao());
        assertEquals(consultaId.toString(), notificacao.getConsulta());
    }

    @Test
    void processar_FluxoCompletoRemanejamento_DeveReagendarConsultas() {
        // Given - Configurar dados e stubs
        LocalDateTime agora = LocalDateTime.now();

        // 1. Criar consulta urgente pendente
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

        // 2. Criar consulta normal já agendada (candidata para remanejamento)
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

        // 3. Salvar no repositório
        consultaRepository.salvar(consultaUrgente);
        consultaRepository.salvar(consultaNormal);

        // 4. Configurar dados dos pacientes
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

        // 5. Configurar dados do médico
        LocalDateTime diaFuturo = ajustarHorarioConsulta(agora.plusDays(5));
        MedicoDTO medicoDTO = MedicoDTO.builder()
                .id("med-123")
                .nome("Dr. Teste")
                .especialidade("CARDIOLOGIA")
                .cidade("São Paulo")
                .horariosTrabalho(List.of(
                        HorarioTrabalho.builder()
                                .diaSemana(dataHoraConsultaNormal.getDayOfWeek()) // Mesmo dia da consulta normal
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build(),
                        HorarioTrabalho.builder()
                                .diaSemana(diaFuturo.getDayOfWeek()) // Dia futuro para nova data
                                .horaInicio(LocalTime.of(8, 0))
                                .horaFim(LocalTime.of(18, 0))
                                .build()
                ))
                .build();

        // 6. Configurar stubs
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteUrgenteDTO);
        when(pacienteServicePort.buscarPacientePorCpf("98765432100")).thenReturn(pacienteNormalDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo"))
                .thenReturn(List.of(medicoDTO));

        // Execute
        useCase.executar();

        // Verify - Verificar resultado

        // 1. Buscar as consultas após processamento
        Consulta consultaUrgenteAtualizada = consultaRepository.buscarPorId(consultaUrgenteId)
                .orElseThrow(() -> new AssertionError("Consulta urgente não encontrada após processamento"));

        Consulta consultaNormalAtualizada = consultaRepository.buscarPorId(consultaNormalId)
                .orElseThrow(() -> new AssertionError("Consulta normal não encontrada após processamento"));

        // 2. Verificar se a consulta urgente foi agendada no horário da consulta normal
        assertEquals(StatusConsulta.AGENDADA, consultaUrgenteAtualizada.getStatus());
        assertEquals("med-123", consultaUrgenteAtualizada.getMedicoId());
        assertEquals(dataHoraConsultaNormal, consultaUrgenteAtualizada.getDataHora());

        // 3. Verificar se a consulta normal foi reagendada
        assertNotEquals(dataHoraConsultaNormal, consultaNormalAtualizada.getDataHora());
        assertTrue(consultaNormalAtualizada.getDataHora().isAfter(dataHoraConsultaNormal));

        // 4. Verificar se a data da consulta reagendada está em incrementos de 30 minutos
        int minutos = consultaNormalAtualizada.getDataHora().getMinute();
        assertTrue(minutos == 0 || minutos == 30,
                "O minuto da consulta reagendada deve ser 0 ou 30, foi: " + minutos);

        // 5. Verificar se as notificações foram enviadas
        verify(notificacaoServicePort, times(2)).enviarNotificacao(any(NotificacaoDTO.class));
        assertEquals(2, notificacoesEnviadas.size());
    }

    @Test
    void processar_QuandoNaoHaMedicosDisponiveis_DeveNotificarListaEspera() {
        // Given - Configurar dados e stubs
        LocalDateTime agora = LocalDateTime.now();

        // 1. Criar consulta pendente
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

        // 2. Salvar no repositório
        consultaRepository.salvar(consultaPendente);

        // 3. Configurar dados do paciente
        PacienteDTO pacienteDTO = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("Paciente Teste")
                .email("paciente@teste.com")
                .telefone("11987654321")
                .cidade("São Paulo")
                .build();

        // 4. Configurar stubs para simular ausência de médicos
        when(pacienteServicePort.buscarPacientePorCpf("12345678900")).thenReturn(pacienteDTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("CARDIOLOGIA", "São Paulo"))
                .thenReturn(List.of()); // Lista vazia de médicos

        // Execute
        useCase.executar();

        // Verify - Verificar resultado

        // 1. Buscar a consulta após processamento
        Consulta consultaAtualizada = consultaRepository.buscarPorId(consultaId)
                .orElseThrow(() -> new AssertionError("Consulta não encontrada após processamento"));

        // 2. Verificar se o status permanece como pendente
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, consultaAtualizada.getStatus());
        assertNull(consultaAtualizada.getMedicoId());
        assertNull(consultaAtualizada.getDataHora());

        // 3. Verificar se a notificação de lista de espera foi enviada
        verify(notificacaoServicePort).enviarNotificacao(any(NotificacaoDTO.class));
        assertEquals(1, notificacoesEnviadas.size());

        NotificacaoDTO notificacao = notificacoesEnviadas.getFirst();
        assertEquals("Paciente Teste", notificacao.getNomePaciente());
        assertEquals("paciente@teste.com", notificacao.getEmail());
        assertEquals(TipoNotificacao.ENTRADA_LISTA_ESPERA, notificacao.getTipoNotificacao());
    }

    @Test
    void processar_MedicoComMenosCarga_DeveDistribuirConsultasIgualmente() {
        // Given - Configurar dados e stubs
        LocalDateTime agora = LocalDateTime.now();

        // 1. Criar duas consultas pendentes para a mesma especialidade e cidade
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

        // 2. Criar uma consulta já agendada para o primeiro médico
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

        // 3. Salvar consultas no repositório
        consultaRepository.salvar(consulta1);
        consultaRepository.salvar(consulta2);
        consultaRepository.salvar(consultaAgendada);

        // 4. Configurar dados dos pacientes
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

        // 5. Configurar dados dos médicos
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

        // 6. Configurar stubs
        when(pacienteServicePort.buscarPacientePorCpf("11111111111")).thenReturn(paciente1DTO);
        when(pacienteServicePort.buscarPacientePorCpf("22222222222")).thenReturn(paciente2DTO);
        when(medicoServicePort.buscarMedicosPorEspecialidadeECidade("DERMATOLOGIA", "São Paulo"))
                .thenReturn(Arrays.asList(medico1DTO, medico2DTO));

        // Execute
        useCase.executar();

        // Verify - Verificar resultado

        // 1. Buscar as consultas após processamento
        Consulta consulta1Atualizada = consultaRepository.buscarPorId(consulta1Id)
                .orElseThrow(() -> new AssertionError("Consulta 1 não encontrada após processamento"));

        Consulta consulta2Atualizada = consultaRepository.buscarPorId(consulta2Id)
                .orElseThrow(() -> new AssertionError("Consulta 2 não encontrada após processamento"));

        // 2. Verificar se as consultas foram distribuídas entre os médicos
        assertNotEquals(consulta1Atualizada.getMedicoId(), consulta2Atualizada.getMedicoId(),
                "As consultas deveriam ser distribuídas entre médicos diferentes");

        // 3. Verificar se pelo menos uma consulta foi agendada com o segundo médico
        boolean peloMenosUmaComMedico2 =
                "med-456".equals(consulta1Atualizada.getMedicoId()) ||
                        "med-456".equals(consulta2Atualizada.getMedicoId());

        assertTrue(peloMenosUmaComMedico2, "Pelo menos uma consulta deveria ser agendada com o médico menos ocupado");

        // 4. Verificar se as datas das consultas estão em incrementos de 30 minutos
        int minutos1 = consulta1Atualizada.getDataHora().getMinute();
        int minutos2 = consulta2Atualizada.getDataHora().getMinute();

        assertTrue(minutos1 == 0 || minutos1 == 30,
                "O minuto da consulta 1 deve ser 0 ou 30, foi: " + minutos1);
        assertTrue(minutos2 == 0 || minutos2 == 30,
                "O minuto da consulta 2 deve ser 0 ou 30, foi: " + minutos2);

        // 5. Verificar notificações
        verify(notificacaoServicePort, times(2)).enviarNotificacao(any(NotificacaoDTO.class));
        assertEquals(2, notificacoesEnviadas.size());
    }
}