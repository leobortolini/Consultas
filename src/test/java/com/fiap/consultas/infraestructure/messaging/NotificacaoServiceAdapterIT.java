package com.fiap.consultas.infraestructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.domain.enums.TipoNotificacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnableTestBinder
@AutoConfigureTestDatabase
class NotificacaoServiceAdapterIT {

    @Autowired
    private NotificacaoServiceAdapter notificacaoServiceAdapter;

    @Autowired
    private OutputDestination outputDestination;

    private static final String DESTINATION_BINDING = "enviarNotificacao-out-0";

    @AfterEach
    void cleanup() {
        outputDestination.clear();
    }

    @Test
    void deveEnviarNotificacaoParaODestinoBrokerCorreto() throws IOException {
        // Arrange
        UUID consultaId = UUID.randomUUID();
        NotificacaoDTO notificacao = NotificacaoDTO.builder()
                .consultaId(consultaId)
                .nomePaciente("João Silva")
                .email("joao.silva@example.com")
                .telefone("11999999999")
                .consulta("Consulta de rotina")
                .localConsulta("Clínica Central")
                .nomeMedico("Dra. Maria Santos")
                .dataConsulta("2023-10-15 14:30")
                .tipoNotificacao(TipoNotificacao.ENTRADA_LISTA_ESPERA)
                .build();

        // Act
        notificacaoServiceAdapter.enviarNotificacao(notificacao);

        // Assert
        Message<byte[]> mensagemRecebida = outputDestination.receive(5000, DESTINATION_BINDING);
        assertNotNull(mensagemRecebida, "A mensagem deveria ter sido enviada");

        ObjectMapper objectMapper = new ObjectMapper();
        NotificacaoDTO notificacaoDTO = objectMapper.readValue(mensagemRecebida.getPayload(), NotificacaoDTO.class);

        assertEquals(consultaId, notificacaoDTO.getConsultaId());
    }
}