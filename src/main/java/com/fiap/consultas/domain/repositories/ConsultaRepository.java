package com.fiap.consultas.domain.repositories;

import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.StatusConsulta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultaRepository {
    Consulta salvar(Consulta consulta);
    Optional<Consulta> buscarPorId(UUID id);
    List<Consulta> buscarPorStatus(StatusConsulta status);
    List<Consulta> buscarConsultasNaoConfirmadasPorEspecialidadeECidade(String especialidade, String cidade);
    List<Consulta> buscarConsultasPendentesAgendamento();
    boolean existeConsultaNoHorario(String medicoId, LocalDateTime dataHora);
    List<Consulta> buscarConsultasPorMedicoEIntervalo(String medicoId, LocalDateTime inicio, LocalDateTime fim);

}
