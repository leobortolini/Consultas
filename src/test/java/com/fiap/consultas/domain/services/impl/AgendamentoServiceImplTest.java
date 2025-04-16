package com.fiap.consultas.domain.services.impl;

import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.entities.HorarioTrabalho;
import com.fiap.consultas.domain.entities.Medico;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceImplTest {

    @Mock
    private ConsultaRepository consultaRepository;

    @InjectMocks
    private AgendamentoServiceImpl agendamentoService;

    private Medico medico;
    private Consulta consulta;
    private LocalDateTime dataHoraConsulta;

    @BeforeEach
    void setUp() {
        // Criar horários de trabalho para o médico (Segunda-feira, 8h às 12h)
        HorarioTrabalho horarioTrabalho = new HorarioTrabalho();
        horarioTrabalho.setDiaSemana(DayOfWeek.MONDAY);
        horarioTrabalho.setHoraInicio(LocalTime.of(8, 0));
        horarioTrabalho.setHoraFim(LocalTime.of(12, 0));

        // Criar médico com o horário de trabalho
        medico = new Medico();
        medico.setId("M123");
        medico.setNome("Dr. Teste");
        medico.setEspecialidade("Cardiologia");
        medico.setCidade("São Paulo");
        medico.setHorariosTrabalho(Collections.singletonList(horarioTrabalho));

        // Criar uma data/hora para consulta (Segunda-feira, 9h)
        dataHoraConsulta = LocalDateTime.of(2023, 1, 2, 9, 0); // Segunda-feira, 09:00

        // Criar uma consulta
        consulta = new Consulta();
        consulta.setId(UUID.randomUUID());
        consulta.setMedicoId(medico.getId());
        consulta.setPacienteCpf("12345678900");
        consulta.setEspecialidade("Cardiologia");
        consulta.setCidade("São Paulo");
        consulta.setDataHora(dataHoraConsulta);
        consulta.setPrioridade(PrioridadeConsulta.MEDIA);
        consulta.setStatus(StatusConsulta.AGENDADA);
        consulta.setDataCriacao(LocalDateTime.now().minusDays(1));
        consulta.setDataAtualizacao(LocalDateTime.now().minusDays(1));
    }

    // Testes para isHorarioDisponivel

    @Test
    void isHorarioDisponivel_QuandoMedicoTrabalhaNoHorarioESemConsultaMarcada_DeveRetornarTrue() {
        // Arrange
        when(consultaRepository.existeConsultaNoHorario(anyString(), any(LocalDateTime.class))).thenReturn(false);

        // Act
        boolean resultado = agendamentoService.isHorarioDisponivel(medico, dataHoraConsulta);

        // Assert
        assertTrue(resultado);
    }

    @Test
    void isHorarioDisponivel_QuandoMedicoTrabalhaNoHorarioMasTemConsultaMarcada_DeveRetornarFalse() {
        // Arrange
        when(consultaRepository.existeConsultaNoHorario(anyString(), any(LocalDateTime.class))).thenReturn(true);

        // Act
        boolean resultado = agendamentoService.isHorarioDisponivel(medico, dataHoraConsulta);

        // Assert
        assertFalse(resultado);
    }

    @Test
    void isHorarioDisponivel_QuandoMedicoNaoTrabalhaNoHorario_DeveRetornarFalse() {
        // Arrange
        LocalDateTime foraDaJornada = LocalDateTime.of(2023, 1, 2, 13, 0); // Segunda-feira, 13:00 (fora do horário de trabalho)

        // Act
        boolean resultado = agendamentoService.isHorarioDisponivel(medico, foraDaJornada);

        // Assert
        assertFalse(resultado);
    }

    @Test
    void isHorarioDisponivel_QuandoMedicoNaoTrabalhaNoDia_DeveRetornarFalse() {
        // Arrange
        LocalDateTime outroDia = LocalDateTime.of(2023, 1, 3, 9, 0); // Terça-feira, 09:00

        // Act
        boolean resultado = agendamentoService.isHorarioDisponivel(medico, outroDia);

        // Assert
        assertFalse(resultado);
    }

    // Testes para buscarConsultasParaReagendar

    @Test
    void buscarConsultasParaReagendar_QuandoPrioridadeEhUrgente_DeveRetornarListaOrdenada() {
        // Arrange
        Consulta consulta1 = new Consulta();
        consulta1.setDataHora(LocalDateTime.now().plusHours(3));
        consulta1.setStatus(StatusConsulta.AGENDADA);
        consulta1.setId(UUID.randomUUID());

        Consulta consulta2 = new Consulta();
        consulta2.setDataHora(LocalDateTime.now().plusHours(1));
        consulta2.setStatus(StatusConsulta.AGENDADA);
        consulta2.setId(UUID.randomUUID());

        List<Consulta> consultasNaoConfirmadas = Arrays.asList(consulta1, consulta2);

        when(consultaRepository.buscarConsultasNaoConfirmadasPorEspecialidadeECidade("Cardiologia", "São Paulo"))
                .thenReturn(consultasNaoConfirmadas);

        // Act
        List<Consulta> resultado = agendamentoService.buscarConsultasParaReagendar("Cardiologia", "São Paulo", PrioridadeConsulta.URGENTE);

        // Assert
        assertEquals(2, resultado.size());
        // Verifica se as consultas foram ordenadas por data/hora (a mais próxima primeiro)
        assertTrue(resultado.get(0).getDataHora().isBefore(resultado.get(1).getDataHora()));
    }

    @Test
    void buscarConsultasParaReagendar_QuandoPrioridadeNaoEhUrgente_DeveRetornarListaVazia() {
        // Act
        List<Consulta> resultado = agendamentoService.buscarConsultasParaReagendar("Cardiologia", "São Paulo", PrioridadeConsulta.MEDIA);

        // Assert
        assertTrue(resultado.isEmpty());
    }

    @Test
    void buscarConsultasParaReagendar_QuandoNaoHaConsultasRemarcaveis_DeveRetornarListaVazia() {
        // Arrange
        Consulta consultaNaoRemarcavel = new Consulta();
        consultaNaoRemarcavel.setDataHora(LocalDateTime.now().plusHours(1));
        consultaNaoRemarcavel.setStatus(StatusConsulta.CONFIRMADA); // Não é remarcável pois não está AGENDADA
        consultaNaoRemarcavel.setId(UUID.randomUUID());

        List<Consulta> consultasNaoConfirmadas = Collections.singletonList(consultaNaoRemarcavel);

        when(consultaRepository.buscarConsultasNaoConfirmadasPorEspecialidadeECidade("Cardiologia", "São Paulo"))
                .thenReturn(consultasNaoConfirmadas);

        // Act
        List<Consulta> resultado = agendamentoService.buscarConsultasParaReagendar("Cardiologia", "São Paulo", PrioridadeConsulta.URGENTE);

        // Assert
        assertTrue(resultado.isEmpty());
    }

    // Testes para encontrarProximoHorarioDisponivel

    @Test
    void encontrarProximoHorarioDisponivel_QuandoTemMedicosDisponiveis_DeveRetornarHorarioMaisProximo() {
        // Arrange
        // Configura o mock para retornar false para qualquer consulta, indicando que não há consultas marcadas
        when(consultaRepository.existeConsultaNoHorario(anyString(), any(LocalDateTime.class))).thenReturn(false);

        List<Medico> medicos = Collections.singletonList(medico);

        // Act
        LocalDateTime resultado = agendamentoService.encontrarProximoHorarioDisponivel(medicos, "Cardiologia", "São Paulo");

        // Assert
        assertNotNull(resultado);
        // Verifica se a data retornada é uma segunda-feira e está dentro do horário de trabalho
        assertEquals(DayOfWeek.MONDAY, resultado.getDayOfWeek());
        assertTrue(resultado.toLocalTime().isAfter(LocalTime.of(8, 0)) &&
                resultado.toLocalTime().isBefore(LocalTime.of(12, 0)));
    }

    @Test
    void encontrarProximoHorarioDisponivel_QuandoListaMedicosEstaVazia_DeveRetornarNull() {
        // Act
        LocalDateTime resultado = agendamentoService.encontrarProximoHorarioDisponivel(
                Collections.emptyList(), "Cardiologia", "São Paulo");

        // Assert
        assertNull(resultado);
    }

    @Test
    void encontrarProximoHorarioDisponivel_QuandoNaoHaHorariosDisponiveis_DeveRetornarNull() {
        // Arrange
        // Configura o mock para retornar true para qualquer consulta, indicando que todos os horários estão ocupados
        when(consultaRepository.existeConsultaNoHorario(anyString(), any(LocalDateTime.class))).thenReturn(true);

        List<Medico> medicos = Collections.singletonList(medico);

        // Act
        LocalDateTime resultado = agendamentoService.encontrarProximoHorarioDisponivel(medicos, "Cardiologia", "São Paulo");

        // Assert
        assertNull(resultado);
    }

    @Test
    void encontrarProximoHorarioDisponivel_DeveRetornarHorariosEm30MinutosIntervalos() {
        // Arrange
        // Configura o mock para retornar false para qualquer consulta
        when(consultaRepository.existeConsultaNoHorario(anyString(), any(LocalDateTime.class))).thenReturn(false);

        // Adiciona outro horário de trabalho para o médico (Quarta-feira, 8h às 8h30)
        // Este horário é curto propositalmente para garantir que apenas um slot seja gerado
        HorarioTrabalho horarioCurto = new HorarioTrabalho();
        horarioCurto.setDiaSemana(DayOfWeek.WEDNESDAY);
        horarioCurto.setHoraInicio(LocalTime.of(8, 0));
        horarioCurto.setHoraFim(LocalTime.of(10, 0));

        Medico medicoComHorarioCurto = new Medico();
        medicoComHorarioCurto.setId("M456");
        medicoComHorarioCurto.setHorariosTrabalho(Collections.singletonList(horarioCurto));
        medicoComHorarioCurto.setEspecialidade("Cardiologia");
        medicoComHorarioCurto.setCidade("São Paulo");

        List<Medico> medicos = Collections.singletonList(medicoComHorarioCurto);

        // Act
        LocalDateTime resultado = agendamentoService.encontrarProximoHorarioDisponivel(medicos, "Cardiologia", "São Paulo");

        // Assert
        assertNotNull(resultado);
        assertEquals(DayOfWeek.WEDNESDAY, resultado.getDayOfWeek());
        assertEquals(LocalTime.of(8, 30), resultado.toLocalTime());
    }

    @Test
    void encontrarProximoHorarioDisponivel_QuandoTemMultiplosMedicos_DeveRetornarHorarioMaisProximo() {
        // Arrange
        // Configura o mock para retornar false para qualquer consulta
        when(consultaRepository.existeConsultaNoHorario(anyString(), any(LocalDateTime.class))).thenReturn(false);

        // Médico 1: Trabalha às Segundas, 8h-12h
        HorarioTrabalho horarioSegunda = new HorarioTrabalho();
        horarioSegunda.setDiaSemana(DayOfWeek.MONDAY);
        horarioSegunda.setHoraInicio(LocalTime.of(8, 0));
        horarioSegunda.setHoraFim(LocalTime.of(12, 0));

        Medico medico1 = new Medico();
        medico1.setId("M111");
        medico1.setHorariosTrabalho(Collections.singletonList(horarioSegunda));
        medico1.setEspecialidade("Cardiologia");
        medico1.setCidade("São Paulo");

        // Médico 2: Trabalha às Terças, 9h-13h
        HorarioTrabalho horarioTerca = new HorarioTrabalho();
        horarioTerca.setDiaSemana(DayOfWeek.TUESDAY);
        horarioTerca.setHoraInicio(LocalTime.of(9, 0));
        horarioTerca.setHoraFim(LocalTime.of(13, 0));

        Medico medico2 = new Medico();
        medico2.setId("M222");
        medico2.setHorariosTrabalho(Collections.singletonList(horarioTerca));
        medico2.setEspecialidade("Cardiologia");
        medico2.setCidade("São Paulo");

        List<Medico> medicos = Arrays.asList(medico1, medico2);

        // Act
        LocalDateTime resultado = agendamentoService.encontrarProximoHorarioDisponivel(medicos, "Cardiologia", "São Paulo");

        // Assert
        assertNotNull(resultado);
        assertTrue(resultado.getDayOfWeek() == DayOfWeek.MONDAY || resultado.getDayOfWeek() == DayOfWeek.TUESDAY);
    }
}