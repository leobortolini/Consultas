package com.fiap.consultas.infraestructure.persistence.repositories;

import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.infraestructure.persistence.entities.ConsultaJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase
@Import(ConsultaRepositoryImpl.class)
class ConsultaRepositoryImplIT {

    @Autowired
    private ConsultaJpaRepository consultaJpaRepository;

    @Autowired
    private ConsultaRepositoryImpl consultaRepository;

    private UUID id;
    private LocalDateTime agora;
    private ConsultaJpaEntity consultaJpaEntity;
    private Consulta consulta;

    @BeforeEach
    void setup() {
        consultaJpaRepository.deleteAll();

        id = UUID.randomUUID();
        agora = LocalDateTime.now();

        consultaJpaEntity = ConsultaJpaEntity.builder()
                .id(id)
                .pacienteCpf("12345678900")
                .medicoId("MEDICO123")
                .especialidade("Cardiologia")
                .cidade("São Paulo")
                .dataHora(agora)
                .localConsulta("Hospital A")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.AGENDADA)
                .dataCriacao(agora)
                .dataAtualizacao(agora)
                .build();

        consulta = Consulta.builder()
                .id(id)
                .pacienteCpf("12345678900")
                .medicoId("MEDICO123")
                .especialidade("Cardiologia")
                .cidade("São Paulo")
                .dataHora(agora)
                .localConsulta("Hospital A")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.AGENDADA)
                .dataCriacao(agora)
                .dataAtualizacao(agora)
                .build();
    }

    @Test
    void deveSalvar() {
        // Arrange & Act
        Consulta resultado = consultaRepository.salvar(consulta);

        // Assert
        assertEquals(consulta.getId(), resultado.getId());
        assertEquals(consulta.getPacienteCpf(), resultado.getPacienteCpf());
        assertEquals(consulta.getMedicoId(), resultado.getMedicoId());
        assertEquals(consulta.getEspecialidade(), resultado.getEspecialidade());
        assertEquals(consulta.getCidade(), resultado.getCidade());
        assertEquals(consulta.getDataHora(), resultado.getDataHora());
        assertEquals(consulta.getLocalConsulta(), resultado.getLocalConsulta());
        assertEquals(consulta.getPrioridade(), resultado.getPrioridade());
        assertEquals(consulta.getStatus(), resultado.getStatus());
        Optional<ConsultaJpaEntity> savedEntity = consultaJpaRepository.findById(id);
        assertTrue(savedEntity.isPresent());
    }

    @Test
    void deveBuscarPorId() {
        // Arrange
        consultaJpaRepository.save(consultaJpaEntity);

        // Act
        Optional<Consulta> resultado = consultaRepository.buscarPorId(id);

        // Assert
        assertTrue(resultado.isPresent());
        assertEquals(consulta.getId(), resultado.get().getId());
        assertEquals(consulta.getPacienteCpf(), resultado.get().getPacienteCpf());
    }

    @Test
    void deveRetornarOptionalVazioQuandoBuscarPorIdNaoEncontrado() {
        // Arrange
        UUID idInexistente = UUID.randomUUID();

        // Act
        Optional<Consulta> resultado = consultaRepository.buscarPorId(idInexistente);

        // Assert
        assertFalse(resultado.isPresent());
    }

    @Test
    void deveBuscarPorStatus() {
        // Arrange
        consultaJpaRepository.save(consultaJpaEntity);

        // Act
        List<Consulta> resultado = consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA);

        // Assert
        assertEquals(1, resultado.size());
        assertEquals(consulta.getId(), resultado.getFirst().getId());
    }

    @Test
    void deveBuscarConsultasNaoConfirmadasPorEspecialidadeECidade() {
        // Arrange
        ConsultaJpaEntity urgenteEntity = ConsultaJpaEntity.builder()
                .id(id)
                .pacienteCpf("12345678900")
                .medicoId("MEDICO123")
                .especialidade("Cardiologia")
                .cidade("São Paulo")
                .dataHora(agora)
                .localConsulta("Hospital A")
                .prioridade(PrioridadeConsulta.BAIXA)
                .status(StatusConsulta.AGENDADA)
                .dataCriacao(agora)
                .dataAtualizacao(agora)
                .build();
        consultaJpaRepository.save(urgenteEntity);

        // Act
        List<Consulta> resultado = consultaRepository.buscarConsultasNaoConfirmadasPorEspecialidadeECidade(
                "Cardiologia", "São Paulo");

        // Assert
        assertEquals(1, resultado.size());
        assertEquals(id, resultado.getFirst().getId());
    }

    @Test
    void deveBuscarConsultasPendentesAgendamento() {
        // Arrange
        ConsultaJpaEntity pendenteEntity = ConsultaJpaEntity.builder()
                .id(id)
                .pacienteCpf("12345678900")
                .medicoId("MEDICO123")
                .especialidade("Cardiologia")
                .cidade("São Paulo")
                .dataHora(agora)
                .localConsulta("Hospital A")
                .prioridade(PrioridadeConsulta.MEDIA)
                .status(StatusConsulta.PENDENTE_AGENDAMENTO)
                .dataCriacao(agora)
                .dataAtualizacao(agora)
                .build();
        consultaJpaRepository.save(pendenteEntity);

        // Act
        List<Consulta> resultado = consultaRepository.buscarConsultasPendentesAgendamento();

        // Assert
        assertEquals(1, resultado.size());
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, resultado.getFirst().getStatus());
    }

    @Test
    void deveRetornarFalsoQuandoNaoExisteConsultaNoHorario() {
        // Act
        boolean resultado = consultaRepository.existeConsultaNoHorario("MEDICO123", agora);

        // Assert
        assertFalse(resultado);
    }

    @Test
    void deveBuscarConsultasPorMedicoEIntervalo() {
        // Arrange
        consultaJpaRepository.save(consultaJpaEntity);

        LocalDateTime inicio = agora.minusHours(1);
        LocalDateTime fim = agora.plusHours(1);

        // Act
        List<Consulta> resultado = consultaRepository.buscarConsultasPorMedicoEIntervalo("MEDICO123", inicio, fim);

        // Assert
        assertEquals(1, resultado.size());
        assertEquals(consulta.getId(), resultado.getFirst().getId());
    }
}