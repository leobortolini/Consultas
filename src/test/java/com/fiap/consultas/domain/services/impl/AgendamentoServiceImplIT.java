package com.fiap.consultas.domain.services.impl;

import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.entities.HorarioTrabalho;
import com.fiap.consultas.domain.entities.Medico;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import com.fiap.consultas.domain.services.AgendamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase
class AgendamentoServiceImplIT {

    @Autowired
    private AgendamentoService agendamentoService;

    @Autowired
    private ConsultaRepository consultaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private Medico medicoCardiologista;
    private Medico medicoNeurologista;
    private LocalDateTime proximaSegunda;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM consultas");

        // Configurar data da próxima segunda e terça-feira
        LocalDateTime hoje = LocalDateTime.now();
        proximaSegunda = hoje.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(9).withMinute(0).withSecond(0).withNano(0);

        // Criar médico cardiologista
        HorarioTrabalho horarioSegunda = new HorarioTrabalho();
        horarioSegunda.setDiaSemana(DayOfWeek.MONDAY);
        horarioSegunda.setHoraInicio(LocalTime.of(8, 0));
        horarioSegunda.setHoraFim(LocalTime.of(12, 0));

        medicoCardiologista = new Medico();
        medicoCardiologista.setId("M123");
        medicoCardiologista.setNome("Dr. Carlos Cardiologista");
        medicoCardiologista.setEspecialidade("Cardiologia");
        medicoCardiologista.setCidade("São Paulo");
        medicoCardiologista.setHorariosTrabalho(Collections.singletonList(horarioSegunda));

        // Criar médico neurologista
        HorarioTrabalho horarioTerca = new HorarioTrabalho();
        horarioTerca.setDiaSemana(DayOfWeek.TUESDAY);
        horarioTerca.setHoraInicio(LocalTime.of(9, 0));
        horarioTerca.setHoraFim(LocalTime.of(15, 0));

        medicoNeurologista = new Medico();
        medicoNeurologista.setId("M456");
        medicoNeurologista.setNome("Dra. Natália Neurologista");
        medicoNeurologista.setEspecialidade("Neurologia");
        medicoNeurologista.setCidade("São Paulo");
        medicoNeurologista.setHorariosTrabalho(Collections.singletonList(horarioTerca));
    }

    @Test
    void deveVerificarDisponibilidadeHorario() {
        // Arrange
        LocalDateTime horarioDisponivel = proximaSegunda.withHour(9).withMinute(0);

        // Act
        boolean disponibilidade = agendamentoService.isHorarioDisponivel(medicoCardiologista, horarioDisponivel);

        // Assert
        assertTrue(disponibilidade, "O horário deveria estar disponível");

        // Criando uma consulta no horário
        Consulta consulta = new Consulta();
        consulta.setId(UUID.randomUUID());
        consulta.setPacienteCpf("12345678900");
        consulta.setMedicoId(medicoCardiologista.getId());
        consulta.setEspecialidade("Cardiologia");
        consulta.setCidade("São Paulo");
        consulta.setDataHora(horarioDisponivel);
        consulta.setPrioridade(PrioridadeConsulta.MEDIA);
        consulta.setStatus(StatusConsulta.AGENDADA);
        consulta.setDataCriacao(LocalDateTime.now());
        consulta.setDataAtualizacao(LocalDateTime.now());
        consultaRepository.salvar(consulta);

        // Act novamente
        disponibilidade = agendamentoService.isHorarioDisponivel(medicoCardiologista, horarioDisponivel);

        // Assert
        assertFalse(disponibilidade, "O horário não deveria estar disponível após agendamento");
    }

    @Test
    void deveVerificarIndisponibilidadeForaDaJornada() {
        // Arrange
        LocalDateTime horarioForaDaJornada = proximaSegunda.withHour(13).withMinute(0);

        // Act
        boolean disponibilidade = agendamentoService.isHorarioDisponivel(medicoCardiologista, horarioForaDaJornada);

        // Assert
        assertFalse(disponibilidade, "O horário fora da jornada não deveria estar disponível");
    }

    @Test
    void deveBuscarConsultasParaReagendar() {
        // Arrange
        // Consulta 1 - Agendada, Cardiologia, São Paulo
        Consulta consulta1 = new Consulta();
        consulta1.setId(UUID.randomUUID());
        consulta1.setPacienteCpf("11111111111");
        consulta1.setMedicoId(medicoCardiologista.getId());
        consulta1.setEspecialidade("Cardiologia");
        consulta1.setCidade("São Paulo");
        consulta1.setDataHora(proximaSegunda.plusHours(1));
        consulta1.setPrioridade(PrioridadeConsulta.MEDIA);
        consulta1.setStatus(StatusConsulta.AGENDADA);
        consulta1.setDataCriacao(LocalDateTime.now());
        consulta1.setDataAtualizacao(LocalDateTime.now());
        consultaRepository.salvar(consulta1);

        // Consulta 2 - Agendada, Cardiologia, São Paulo (mais próxima)
        Consulta consulta2 = new Consulta();
        consulta2.setId(UUID.randomUUID());
        consulta2.setPacienteCpf("22222222222");
        consulta2.setMedicoId(medicoCardiologista.getId());
        consulta2.setEspecialidade("Cardiologia");
        consulta2.setCidade("São Paulo");
        consulta2.setDataHora(proximaSegunda);
        consulta2.setPrioridade(PrioridadeConsulta.MEDIA);
        consulta2.setStatus(StatusConsulta.AGENDADA);
        consulta2.setDataCriacao(LocalDateTime.now());
        consulta2.setDataAtualizacao(LocalDateTime.now());
        consultaRepository.salvar(consulta2);

        // Consulta 3 - Confirmada, não deve ser incluída
        Consulta consulta3 = new Consulta();
        consulta3.setId(UUID.randomUUID());
        consulta3.setPacienteCpf("33333333333");
        consulta3.setMedicoId(medicoCardiologista.getId());
        consulta3.setEspecialidade("Cardiologia");
        consulta3.setCidade("São Paulo");
        consulta3.setDataHora(proximaSegunda.plusHours(2));
        consulta3.setPrioridade(PrioridadeConsulta.MEDIA);
        consulta3.setStatus(StatusConsulta.CONFIRMADA);
        consulta3.setDataCriacao(LocalDateTime.now());
        consulta3.setDataAtualizacao(LocalDateTime.now());
        consultaRepository.salvar(consulta3);

        // Act
        List<Consulta> consultasParaReagendar = agendamentoService.buscarConsultasParaReagendar(
                "Cardiologia", "São Paulo", PrioridadeConsulta.URGENTE);

        // Assert
        assertEquals(2, consultasParaReagendar.size(), "Devem ser encontradas 2 consultas para reagendar");
        assertEquals(consulta2.getId(), consultasParaReagendar.get(0).getId(),
                "A primeira consulta deve ser a mais próxima (consulta2)");
        assertEquals(consulta1.getId(), consultasParaReagendar.get(1).getId(),
                "A segunda consulta deve ser a menos próxima (consulta1)");
    }

    @Test
    void deveEncontrarProximoHorarioDisponivel() {
        // Arrange - Criar uma consulta no primeiro slot disponível do cardiologista
        LocalDateTime primeiroSlotCardiologista = proximaSegunda.withHour(8).withMinute(0);
        Consulta consulta = new Consulta();
        consulta.setId(UUID.randomUUID());
        consulta.setPacienteCpf("12345678900");
        consulta.setMedicoId(medicoCardiologista.getId());
        consulta.setEspecialidade("Cardiologia");
        consulta.setCidade("São Paulo");
        consulta.setDataHora(primeiroSlotCardiologista);
        consulta.setPrioridade(PrioridadeConsulta.MEDIA);
        consulta.setStatus(StatusConsulta.AGENDADA);
        consulta.setDataCriacao(LocalDateTime.now());
        consulta.setDataAtualizacao(LocalDateTime.now());
        consultaRepository.salvar(consulta);

        // Act
        List<Medico> medicos = Arrays.asList(medicoCardiologista, medicoNeurologista);
        LocalDateTime proximoHorario = agendamentoService.encontrarProximoHorarioDisponivel(
                medicos, "Cardiologia", "São Paulo");

        // Assert
        assertNotNull(proximoHorario, "Deve encontrar um próximo horário disponível");
        assertTrue(proximoHorario.isAfter(primeiroSlotCardiologista),
                "O próximo horário deve ser após o slot já ocupado");

        // Verificar se o horário está dentro do intervalo esperado
        LocalTime horaEncontrada = proximoHorario.toLocalTime();
        assertTrue(horaEncontrada.equals(LocalTime.of(8, 30)) ||
                        horaEncontrada.isAfter(LocalTime.of(8, 30)) &&
                                horaEncontrada.isBefore(LocalTime.of(12, 0)),
                "O horário deve estar entre 8:30 e 12:00");
    }

    @Test
    void deveEncontrarHorarioEmOutroMedicoQuandoPrimeiroEstaOcupado() {
        // Arrange - Ocupar todos os slots do cardiologista
        LocalTime inicio = LocalTime.of(8, 0);
        LocalTime fim = LocalTime.of(12, 0);

        for (LocalTime hora = inicio;
             hora.isBefore(fim);
             hora = hora.plusMinutes(30)) {

            LocalDateTime horarioConsulta = proximaSegunda.with(hora);

            Consulta consulta = new Consulta();
            consulta.setId(UUID.randomUUID());
            consulta.setPacienteCpf("12345678900");
            consulta.setMedicoId(medicoCardiologista.getId());
            consulta.setEspecialidade("Cardiologia");
            consulta.setCidade("São Paulo");
            consulta.setDataHora(horarioConsulta);
            consulta.setPrioridade(PrioridadeConsulta.MEDIA);
            consulta.setStatus(StatusConsulta.AGENDADA);
            consulta.setDataCriacao(LocalDateTime.now());
            consulta.setDataAtualizacao(LocalDateTime.now());
            consultaRepository.salvar(consulta);
        }

        // Adicionar outro cardiologista que trabalha na terça
        HorarioTrabalho horarioTerca = new HorarioTrabalho();
        horarioTerca.setDiaSemana(DayOfWeek.TUESDAY);
        horarioTerca.setHoraInicio(LocalTime.of(8, 0));
        horarioTerca.setHoraFim(LocalTime.of(12, 0));

        Medico outroCardiologista = new Medico();
        outroCardiologista.setId("M789");
        outroCardiologista.setNome("Dr. Carlos Cardiologista 2");
        outroCardiologista.setEspecialidade("Cardiologia");
        outroCardiologista.setCidade("São Paulo");
        outroCardiologista.setHorariosTrabalho(Collections.singletonList(horarioTerca));

        // Act
        List<Medico> medicos = Arrays.asList(medicoCardiologista, outroCardiologista);
        LocalDateTime proximoHorario = agendamentoService.encontrarProximoHorarioDisponivel(
                medicos, "Cardiologia", "São Paulo");

        // Assert
        assertNotNull(proximoHorario, "Deve encontrar um próximo horário disponível com o segundo médico");
        assertEquals(DayOfWeek.TUESDAY, proximoHorario.getDayOfWeek(),
                "O próximo horário disponível deve ser na terça-feira");
    }
}