package com.fiap.consultas.infraestructure.messaging;

import com.fiap.consultas.application.dtos.ConfirmacaoConsultaDTO;
import com.fiap.consultas.application.usecases.ReceberConfirmacaoConsultaUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmacaoConsultaConsumerTest {

    @Mock
    private ReceberConfirmacaoConsultaUseCase receberConfirmacaoConsultaUseCase;

    private Consumer<Message<ConfirmacaoConsultaDTO>> messageConsumer;

    @BeforeEach
    void setUp() {
        ConfirmacaoConsultaConsumer confirmacaoConsultaConsumer = new ConfirmacaoConsultaConsumer(receberConfirmacaoConsultaUseCase);
        messageConsumer = confirmacaoConsultaConsumer.receberConfirmacaoConsulta();
    }

    @Test
    void deveChamarUseCaseAoReceberMensagemValida() {
        // Arrange
        String consultaId = "123";
        ConfirmacaoConsultaDTO dto = ConfirmacaoConsultaDTO.builder()
                .consultaId(consultaId)
                .confirmada(true)
                .build();
        Message<ConfirmacaoConsultaDTO> message = MessageBuilder.withPayload(dto).build();

        // Act
        messageConsumer.accept(message);

        // Assert
        verify(receberConfirmacaoConsultaUseCase, times(1)).executar(dto);
    }

    @Test
    void deveCapturarExcecaoQuandoUseCaseFalhar() {
        // Arrange
        String consultaId = "456";
        ConfirmacaoConsultaDTO dto = ConfirmacaoConsultaDTO.builder()
                .consultaId(consultaId)
                .confirmada(true)
                .build();
        Message<ConfirmacaoConsultaDTO> message = MessageBuilder.withPayload(dto).build();

        doThrow(new RuntimeException("Erro simulado")).when(receberConfirmacaoConsultaUseCase).executar(dto);

        // Act
        messageConsumer.accept(message);

        // Assert
        verify(receberConfirmacaoConsultaUseCase, times(1)).executar(dto);
    }

    @Test
    void deveProcessarMensagemQuandoConsultaNaoConfirmada() {
        // Arrange
        String consultaId = "789";
        ConfirmacaoConsultaDTO dto = ConfirmacaoConsultaDTO.builder()
                .consultaId(consultaId)
                .confirmada(false)
                .build();
        Message<ConfirmacaoConsultaDTO> message = MessageBuilder.withPayload(dto).build();

        // Act
        messageConsumer.accept(message);

        // Assert
        verify(receberConfirmacaoConsultaUseCase, times(1)).executar(dto);
    }
}