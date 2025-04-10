package com.fiap.consultas.infraestructure.persistence.repositories;

import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import com.fiap.consultas.infraestructure.persistence.entities.ConsultaJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ConsultaRepositoryImpl implements ConsultaRepository {

    private final ConsultaJpaRepository consultaJpaRepository;

    @Override
    public Consulta salvar(Consulta consulta) {
        ConsultaJpaEntity entity = mapToEntity(consulta);
        ConsultaJpaEntity savedEntity = consultaJpaRepository.save(entity);
        return mapToDomain(savedEntity);
    }

    @Override
    public Optional<Consulta> buscarPorId(UUID id) {
        return consultaJpaRepository.findById(id).map(this::mapToDomain);
    }

    @Override
    public List<Consulta> buscarPorStatus(StatusConsulta status) {
        return consultaJpaRepository.findByStatus(status).stream()
                .map(this::mapToDomain)
                .toList();
    }

    @Override
    public List<Consulta> buscarConsultasNaoConfirmadasPorEspecialidadeECidade(String especialidade, String cidade) {
        return consultaJpaRepository.findByStatusEspecialidadeAndCidade(StatusConsulta.AGENDADA, especialidade, cidade, PrioridadeConsulta.URGENTE).stream()
                .map(this::mapToDomain)
                .toList();
    }

    @Override
    public List<Consulta> buscarConsultasPendentesAgendamento() {
        return consultaJpaRepository.findByStatus(StatusConsulta.PENDENTE_AGENDAMENTO).stream()
                .map(this::mapToDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public boolean existeConsultaNoHorario(String medicoId, LocalDateTime dataHora) {
        return !consultaJpaRepository.findByMedicoIdAndDataHora(medicoId, dataHora).isEmpty();
    }

    @Override
    public List<Consulta> buscarConsultasPorMedicoEIntervalo(String medicoId, LocalDateTime inicio, LocalDateTime fim) {
        return consultaJpaRepository.findByMedicoIdAndDataHoraBetween(medicoId, inicio, fim).stream()
                .map(this::mapToDomain)
                .toList();
    }

    private ConsultaJpaEntity mapToEntity(Consulta consulta) {
        return ConsultaJpaEntity.builder()
                .id(consulta.getId())
                .pacienteCpf(consulta.getPacienteCpf())
                .medicoId(consulta.getMedicoId())
                .especialidade(consulta.getEspecialidade())
                .cidade(consulta.getCidade())
                .dataHora(consulta.getDataHora())
                .localConsulta(consulta.getLocalConsulta())
                .prioridade(consulta.getPrioridade())
                .status(consulta.getStatus())
                .dataCriacao(consulta.getDataCriacao())
                .dataAtualizacao(consulta.getDataAtualizacao())
                .build();
    }

    private Consulta mapToDomain(ConsultaJpaEntity entity) {
        return Consulta.builder()
                .id(entity.getId())
                .pacienteCpf(entity.getPacienteCpf())
                .medicoId(entity.getMedicoId())
                .especialidade(entity.getEspecialidade())
                .cidade(entity.getCidade())
                .dataHora(entity.getDataHora())
                .localConsulta(entity.getLocalConsulta())
                .prioridade(entity.getPrioridade())
                .status(entity.getStatus())
                .dataCriacao(entity.getDataCriacao())
                .dataAtualizacao(entity.getDataAtualizacao())
                .build();
    }
}
