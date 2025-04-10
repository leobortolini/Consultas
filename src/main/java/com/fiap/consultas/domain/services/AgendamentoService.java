package com.fiap.consultas.domain.services;

import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.entities.Medico;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;

import java.time.LocalDateTime;
import java.util.List;

public interface AgendamentoService {
    boolean isHorarioDisponivel(Medico medico, LocalDateTime dataHora);
    List<Consulta> buscarConsultasParaReagendar(String especialidade, String cidade, PrioridadeConsulta prioridade);
    LocalDateTime encontrarProximoHorarioDisponivel(
            List<Medico> medicos, String especialidade, String cidade);
}