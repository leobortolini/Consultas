package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.ConfirmacaoConsultaDTO;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase
class ReceberConfirmacaoConsultaUseCaseIT {

    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private ReceberConfirmacaoConsultaUseCase receberConfirmacaoConsultaUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID consultaId;
    private ConfirmacaoConsultaDTO confirmacaoDTO;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM consultas");

        Consulta consulta = new Consulta();
        consulta.setId(UUID.randomUUID());
        consulta.setStatus(StatusConsulta.AGENDADA);
        consulta.setDataHora(LocalDateTime.now().plusDays(1));
        consulta.setCidade("CIDADE");
        consulta.setPrioridade(PrioridadeConsulta.ALTA);
        consulta.setDataCriacao(LocalDateTime.now());
        consulta.setDataAtualizacao(LocalDateTime.now());
        consulta.setEspecialidade("ESPECIALIDADE");
        consulta.setPacienteCpf("000.000.000-00");

        consulta = consultaRepository.salvar(consulta);
        consultaId = consulta.getId();

        confirmacaoDTO = new ConfirmacaoConsultaDTO();
        confirmacaoDTO.setConsultaId(consultaId.toString());
    }

    @Test
    void deveConfirmarConsultaQuandoConfirmacaoTrue() {
        // Arrange
        confirmacaoDTO.setConfirmada(true);

        // Act
        receberConfirmacaoConsultaUseCase.executar(confirmacaoDTO);

        // Assert
        Optional<Consulta> consultaAtualizada = consultaRepository.buscarPorId(consultaId);
        assertTrue(consultaAtualizada.isPresent());
        assertEquals(StatusConsulta.CONFIRMADA, consultaAtualizada.get().getStatus());
    }

    @Test
    void deveCancelarConsultaQuandoConfirmacaoFalse() {
        // Arrange
        confirmacaoDTO.setConfirmada(false);

        // Act
        receberConfirmacaoConsultaUseCase.executar(confirmacaoDTO);

        // Assert
        Optional<Consulta> consultaAtualizada = consultaRepository.buscarPorId(consultaId);
        assertTrue(consultaAtualizada.isPresent());
        assertEquals(StatusConsulta.CANCELADA, consultaAtualizada.get().getStatus());
    }

    @Test
    void deveLancarExcecaoQuandoConsultaNaoEncontrada() {
        // Arrange
        UUID idInexistente = UUID.randomUUID();
        confirmacaoDTO.setConsultaId(idInexistente.toString());

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            receberConfirmacaoConsultaUseCase.executar(confirmacaoDTO);
        });

        assertEquals("Consulta não encontrada", exception.getMessage());
    }
}