package com.fiap.consultas.domain.services.impl;

import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.entities.HorarioTrabalho;
import com.fiap.consultas.domain.entities.Medico;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import com.fiap.consultas.domain.services.AgendamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgendamentoServiceImpl implements AgendamentoService {

    private final ConsultaRepository consultaRepository;
    private static final int DURACAO_CONSULTA_MINUTOS = 30;

    @Override
    public boolean isHorarioDisponivel(Medico medico, LocalDateTime dataHora) {
        DayOfWeek diaSemana = dataHora.getDayOfWeek();
        LocalTime horario = dataHora.toLocalTime();

        boolean horarioTrabalho = medico.getHorariosTrabalho().stream()
                .filter(ht -> ht.getDiaSemana().equals(diaSemana))
                .anyMatch(ht -> horario.isAfter(ht.getHoraInicio()) &&
                        horario.isBefore(ht.getHoraFim().minusMinutes(DURACAO_CONSULTA_MINUTOS)));

        if (!horarioTrabalho) {
            return false;
        }

        return !consultaRepository.existeConsultaNoHorario(medico.getId(), dataHora);
    }

    @Override
    public List<Consulta> buscarConsultasParaReagendar(String especialidade, String cidade, PrioridadeConsulta prioridade) {
        if (!PrioridadeConsulta.URGENTE.equals(prioridade)) {
            return List.of();
        }

        List<Consulta> consultasNaoConfirmadas = consultaRepository
                .buscarConsultasNaoConfirmadasPorEspecialidadeECidade(especialidade, cidade);

        return consultasNaoConfirmadas.stream()
                .filter(Consulta::isRemarcavel)
                .sorted(Comparator.comparing(Consulta::getDataHora))
                .toList();
    }

    @Override
    public LocalDateTime encontrarProximoHorarioDisponivel(List<Medico> medicos, String especialidade, String cidade) {
        if (medicos.isEmpty()) {
            return null;
        }
        List<HorarioDisponivel> todosHorariosDisponiveis = new ArrayList<>();
        LocalDateTime dataHoraInicial = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS);

        for (int dia = 0; dia < 30; dia++) {
            LocalDateTime dataAtual = dataHoraInicial.plusDays(dia);

            for (Medico medico : medicos) {
                List<LocalDateTime> horariosDisponiveis = buscarHorariosDisponiveisMedico(medico, dataAtual);

                for (LocalDateTime horario : horariosDisponiveis) {
                    todosHorariosDisponiveis.add(new HorarioDisponivel(medico, horario));
                }
            }
        }

        if (todosHorariosDisponiveis.isEmpty()) {
            return null;
        }

        todosHorariosDisponiveis.sort(Comparator.comparing(HorarioDisponivel::horario));

        return todosHorariosDisponiveis.getFirst().horario();
    }

    private record HorarioDisponivel(Medico medico, LocalDateTime horario) { }

    private List<LocalDateTime> buscarHorariosDisponiveisMedico(Medico medico, LocalDateTime data) {
        List<LocalDateTime> horariosDisponiveis = new ArrayList<>();
        DayOfWeek diaSemana = data.getDayOfWeek();

        List<HorarioTrabalho> horariosTrabalho = medico.getHorariosTrabalho().stream()
                .filter(ht -> ht.getDiaSemana().equals(diaSemana))
                .toList();

        for (HorarioTrabalho ht : horariosTrabalho) {
            LocalTime horaInicio = ht.getHoraInicio();
            LocalTime horaFim = ht.getHoraFim();

            for (LocalTime hora = horaInicio;
                 hora.plusMinutes(DURACAO_CONSULTA_MINUTOS).isBefore(horaFim) ||
                         hora.plusMinutes(DURACAO_CONSULTA_MINUTOS).equals(horaFim);
                 hora = hora.plusMinutes(DURACAO_CONSULTA_MINUTOS)) {

                LocalDateTime horarioConsulta = data.with(hora);

                if (isHorarioDisponivel(medico, horarioConsulta)) {
                    horariosDisponiveis.add(horarioConsulta);
                }
            }
        }

        return horariosDisponiveis;
    }
}