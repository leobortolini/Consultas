package com.fiap.consultas.infraestructure.persistence.repositories;

import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.infraestructure.persistence.entities.ConsultaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ConsultaJpaRepository extends JpaRepository<ConsultaJpaEntity, UUID> {

    List<ConsultaJpaEntity> findByStatus(StatusConsulta status);

    @Query("SELECT c FROM ConsultaJpaEntity c WHERE c.status = :status AND c.prioridade != :prioridade AND c.especialidade = :especialidade AND c.cidade = :cidade ORDER BY c.dataHora")
    List<ConsultaJpaEntity> findByStatusEspecialidadeAndCidade(
            @Param("status") StatusConsulta status,
            @Param("especialidade") String especialidade,
            @Param("cidade") String cidade,
            @Param("prioridade") PrioridadeConsulta prioridadeConsulta);


    @Query("SELECT c FROM ConsultaJpaEntity c WHERE c.medicoId = :medicoId AND c.dataHora = :dataHora AND c.status IN ('AGENDADA', 'CONFIRMADA')")
    List<ConsultaJpaEntity> findByMedicoIdAndDataHora(
            @Param("medicoId") String medicoId,
            @Param("dataHora") LocalDateTime dataHora);

    @Query("SELECT c FROM ConsultaJpaEntity c WHERE c.medicoId = :medicoId AND c.dataHora >= :inicio AND c.dataHora < :fim")
    List<ConsultaJpaEntity> findByMedicoIdAndDataHoraBetween(
            @Param("medicoId") String medicoId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim);
}
