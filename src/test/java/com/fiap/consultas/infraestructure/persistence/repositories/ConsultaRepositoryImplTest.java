package com.fiap.consultas.infraestructure.persistence.repositories;

import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.infraestructure.persistence.entities.ConsultaJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsultaRepositoryImplTest {

    @Mock
    private ConsultaJpaRepository consultaJpaRepository;

    @InjectMocks
    private ConsultaRepositoryImpl consultaRepository;

    private UUID id;
    private LocalDateTime agora;
    private ConsultaJpaEntity consultaJpaEntity;
    private Consulta consulta;

    @BeforeEach
    void setup() {
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
    void testSalvar() {
        // Arrange
        when(consultaJpaRepository.save(any(ConsultaJpaEntity.class))).thenReturn(consultaJpaEntity);

        // Act
        Consulta resultado = consultaRepository.salvar(consulta);

        // Assert
        assertNotNull(resultado);
        assertEquals(consulta.getId(), resultado.getId());
        assertEquals(consulta.getPacienteCpf(), resultado.getPacienteCpf());
        verify(consultaJpaRepository, times(1)).save(any(ConsultaJpaEntity.class));
    }

    @Test
    void testBuscarPorId() {
        // Arrange
        when(consultaJpaRepository.findById(id)).thenReturn(Optional.of(consultaJpaEntity));

        // Act
        Optional<Consulta> resultado = consultaRepository.buscarPorId(id);

        // Assert
        assertTrue(resultado.isPresent());
        assertEquals(consulta.getId(), resultado.get().getId());
        verify(consultaJpaRepository, times(1)).findById(id);
    }

    @Test
    void testBuscarPorIdNaoEncontrado() {
        // Arrange
        UUID idInexistente = UUID.randomUUID();
        when(consultaJpaRepository.findById(idInexistente)).thenReturn(Optional.empty());

        // Act
        Optional<Consulta> resultado = consultaRepository.buscarPorId(idInexistente);

        // Assert
        assertFalse(resultado.isPresent());
        verify(consultaJpaRepository, times(1)).findById(idInexistente);
    }

    @Test
    void testBuscarPorStatus() {
        // Arrange
        List<ConsultaJpaEntity> consultaJpaEntities = List.of(consultaJpaEntity);
        when(consultaJpaRepository.findByStatus(StatusConsulta.AGENDADA)).thenReturn(consultaJpaEntities);

        // Act
        List<Consulta> resultado = consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA);

        // Assert
        assertEquals(1, resultado.size());
        assertEquals(consulta.getId(), resultado.getFirst().getId());
        verify(consultaJpaRepository, times(1)).findByStatus(StatusConsulta.AGENDADA);
    }

    @Test
    void testBuscarConsultasNaoConfirmadasPorEspecialidadeECidade() {
        // Arrange
        List<ConsultaJpaEntity> consultaJpaEntities = List.of(consultaJpaEntity);
        when(consultaJpaRepository.findByStatusEspecialidadeAndCidade(
                StatusConsulta.AGENDADA, "Cardiologia", "São Paulo", PrioridadeConsulta.URGENTE))
                .thenReturn(consultaJpaEntities);

        // Act
        List<Consulta> resultado = consultaRepository.buscarConsultasNaoConfirmadasPorEspecialidadeECidade(
                "Cardiologia", "São Paulo");

        // Assert
        assertEquals(1, resultado.size());
        assertEquals(consulta.getId(), resultado.getFirst().getId());
        verify(consultaJpaRepository, times(1)).findByStatusEspecialidadeAndCidade(
                StatusConsulta.AGENDADA, "Cardiologia", "São Paulo", PrioridadeConsulta.URGENTE);
    }

    @Test
    void testBuscarConsultasPendentesAgendamento() {
        // Arrange
        List<ConsultaJpaEntity> consultaJpaEntities = List.of(consultaJpaEntity);
        when(consultaJpaRepository.findByStatus(StatusConsulta.PENDENTE_AGENDAMENTO)).thenReturn(consultaJpaEntities);

        // Act
        List<Consulta> resultado = consultaRepository.buscarConsultasPendentesAgendamento();

        // Assert
        assertEquals(1, resultado.size());
        verify(consultaJpaRepository, times(1)).findByStatus(StatusConsulta.PENDENTE_AGENDAMENTO);
    }

    @Test
    void testExisteConsultaNoHorario_Existe() {
        // Arrange
        List<ConsultaJpaEntity> consultaJpaEntities = List.of(consultaJpaEntity);
        when(consultaJpaRepository.findByMedicoIdAndDataHora("MEDICO123", agora)).thenReturn(consultaJpaEntities);

        // Act
        boolean resultado = consultaRepository.existeConsultaNoHorario("MEDICO123", agora);

        // Assert
        assertTrue(resultado);
        verify(consultaJpaRepository, times(1)).findByMedicoIdAndDataHora("MEDICO123", agora);
    }

    @Test
    void testExisteConsultaNoHorario_NaoExiste() {
        // Arrange
        when(consultaJpaRepository.findByMedicoIdAndDataHora("MEDICO123", agora)).thenReturn(new ArrayList<>());

        // Act
        boolean resultado = consultaRepository.existeConsultaNoHorario("MEDICO123", agora);

        // Assert
        assertFalse(resultado);
        verify(consultaJpaRepository, times(1)).findByMedicoIdAndDataHora("MEDICO123", agora);
    }

    @Test
    void testBuscarConsultasPorMedicoEIntervalo() {
        // Arrange
        LocalDateTime inicio = agora.minusHours(1);
        LocalDateTime fim = agora.plusHours(1);
        List<ConsultaJpaEntity> consultaJpaEntities = List.of(consultaJpaEntity);
        when(consultaJpaRepository.findByMedicoIdAndDataHoraBetween("MEDICO123", inicio, fim))
                .thenReturn(consultaJpaEntities);

        // Act
        List<Consulta> resultado = consultaRepository.buscarConsultasPorMedicoEIntervalo("MEDICO123", inicio, fim);

        // Assert
        assertEquals(1, resultado.size());
        assertEquals(consulta.getId(), resultado.getFirst().getId());
        verify(consultaJpaRepository, times(1))
                .findByMedicoIdAndDataHoraBetween("MEDICO123", inicio, fim);
    }
}