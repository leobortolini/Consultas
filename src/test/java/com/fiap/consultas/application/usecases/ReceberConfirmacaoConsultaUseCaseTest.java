package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.ConfirmacaoConsultaDTO;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceberConfirmacaoConsultaUseCaseTest {

    @Mock
    private ConsultaRepository consultaRepository;

    @InjectMocks
    private ReceberConfirmacaoConsultaUseCase receberConfirmacaoConsultaUseCase;

    private UUID consultaId;
    private Consulta consulta;
    private ConfirmacaoConsultaDTO confirmacaoDTO;

    @BeforeEach
    void setUp() {
        consultaId = UUID.randomUUID();
        consulta = new Consulta();
        consulta.setId(consultaId);
        consulta.setStatus(StatusConsulta.AGENDADA);

        confirmacaoDTO = new ConfirmacaoConsultaDTO();
        confirmacaoDTO.setConsultaId(consultaId.toString());
    }

    @Test
    void deveConfirmarConsultaQuandoConfirmacaoTrue() {
        // Arrange
        confirmacaoDTO.setConfirmada(true);
        when(consultaRepository.buscarPorId(consultaId)).thenReturn(Optional.of(consulta));

        // Act
        receberConfirmacaoConsultaUseCase.executar(confirmacaoDTO);

        // Assert
        assertEquals(StatusConsulta.CONFIRMADA, consulta.getStatus());
        verify(consultaRepository, times(1)).salvar(consulta);
    }

    @Test
    void deveCancelarConsultaQuandoConfirmacaoFalse() {
        // Arrange
        confirmacaoDTO.setConfirmada(false);
        when(consultaRepository.buscarPorId(consultaId)).thenReturn(Optional.of(consulta));

        // Act
        receberConfirmacaoConsultaUseCase.executar(confirmacaoDTO);

        // Assert
        assertEquals(StatusConsulta.CANCELADA, consulta.getStatus());
        verify(consultaRepository, times(1)).salvar(consulta);
    }

    @Test
    void deveIgnorarQuandoConsultaNaoEncontrada() {
        // Arrange
        when(consultaRepository.buscarPorId(any(UUID.class))).thenReturn(Optional.empty());

        // Act
        boolean executado = receberConfirmacaoConsultaUseCase.executar(confirmacaoDTO);

        // Assert
        verify(consultaRepository, never()).salvar(any(Consulta.class));
        assertFalse(executado);
    }

    @Test
    void deveProcessarCorretamenteUUIDNaConfirmacao() {
        // Arrange
        String uuidString = consultaId.toString();
        confirmacaoDTO.setConsultaId(uuidString);
        confirmacaoDTO.setConfirmada(true);

        when(consultaRepository.buscarPorId(consultaId)).thenReturn(Optional.of(consulta));

        // Act
        receberConfirmacaoConsultaUseCase.executar(confirmacaoDTO);

        // Assert
        verify(consultaRepository, times(1)).buscarPorId(consultaId);
        verify(consultaRepository, times(1)).salvar(consulta);
    }

    @Test
    void deveLancarExcecaoQuandoUUIDInvalido() {
        // Arrange
        confirmacaoDTO.setConsultaId("uuid-invalido");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            receberConfirmacaoConsultaUseCase.executar(confirmacaoDTO);
        });

        verify(consultaRepository, never()).buscarPorId(any());
        verify(consultaRepository, never()).salvar(any());
    }
}