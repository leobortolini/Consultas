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
        // Limpa o banco de dados antes de cada teste
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
    void testSalvar() {
        Consulta resultado = consultaRepository.salvar(consulta);

        assertEquals(consulta.getId(), resultado.getId());
        assertEquals(consulta.getPacienteCpf(), resultado.getPacienteCpf());
        assertEquals(consulta.getMedicoId(), resultado.getMedicoId());
        assertEquals(consulta.getEspecialidade(), resultado.getEspecialidade());
        assertEquals(consulta.getCidade(), resultado.getCidade());
        assertEquals(consulta.getDataHora(), resultado.getDataHora());
        assertEquals(consulta.getLocalConsulta(), resultado.getLocalConsulta());
        assertEquals(consulta.getPrioridade(), resultado.getPrioridade());
        assertEquals(consulta.getStatus(), resultado.getStatus());

        // Verifica se foi salvo no banco de dados
        Optional<ConsultaJpaEntity> savedEntity = consultaJpaRepository.findById(id);
        assertTrue(savedEntity.isPresent());
    }

    @Test
    void testBuscarPorId() {
        // Salva primeiro
        consultaJpaRepository.save(consultaJpaEntity);

        // Depois busca
        Optional<Consulta> resultado = consultaRepository.buscarPorId(id);

        assertTrue(resultado.isPresent());
        assertEquals(consulta.getId(), resultado.get().getId());
        assertEquals(consulta.getPacienteCpf(), resultado.get().getPacienteCpf());
    }

    @Test
    void testBuscarPorIdNaoEncontrado() {
        UUID idInexistente = UUID.randomUUID();
        Optional<Consulta> resultado = consultaRepository.buscarPorId(idInexistente);

        assertFalse(resultado.isPresent());
    }

    @Test
    void testBuscarPorStatus() {
        // Salva a entidade
        consultaJpaRepository.save(consultaJpaEntity);

        // Testa busca por status
        List<Consulta> resultado = consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA);

        assertEquals(1, resultado.size());
        assertEquals(consulta.getId(), resultado.getFirst().getId());
    }

    @Test
    void testBuscarConsultasNaoConfirmadasPorEspecialidadeECidade() {
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

        List<Consulta> resultado = consultaRepository.buscarConsultasNaoConfirmadasPorEspecialidadeECidade(
                "Cardiologia", "São Paulo");

        assertEquals(1, resultado.size());
        assertEquals(id, resultado.getFirst().getId());
    }

    @Test
    void testBuscarConsultasPendentesAgendamento() {
        // Define status como PENDENTE_AGENDAMENTO
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

        List<Consulta> resultado = consultaRepository.buscarConsultasPendentesAgendamento();

        assertEquals(1, resultado.size());
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, resultado.getFirst().getStatus());
    }

    @Test
    void testExisteConsultaNoHorario_NaoExiste() {
        // Sem salvar nada no banco, deve retornar falso
        boolean resultado = consultaRepository.existeConsultaNoHorario("MEDICO123", agora);

        assertFalse(resultado);
    }

    @Test
    void testBuscarConsultasPorMedicoEIntervalo() {
        // Salva a entidade
        consultaJpaRepository.save(consultaJpaEntity);

        LocalDateTime inicio = agora.minusHours(1);
        LocalDateTime fim = agora.plusHours(1);

        List<Consulta> resultado = consultaRepository.buscarConsultasPorMedicoEIntervalo("MEDICO123", inicio, fim);

        assertEquals(1, resultado.size());
        assertEquals(consulta.getId(), resultado.getFirst().getId());
    }
}