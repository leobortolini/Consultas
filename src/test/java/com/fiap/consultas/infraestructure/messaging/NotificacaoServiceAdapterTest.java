package com.fiap.consultas.infraestructure.messaging;

import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.domain.enums.TipoNotificacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceAdapterTest {

    @Mock
    private StreamBridge streamBridge;

    private NotificacaoServiceAdapter notificacaoServiceAdapter;

    private static final String DESTINATION_BINDING = "enviarNotificacao-out-0";

    @BeforeEach
    void setUp() {
        notificacaoServiceAdapter = new NotificacaoServiceAdapter(streamBridge);
    }

    @Test
    void deveEnviarNotificacaoComSucesso() {
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
        verify(streamBridge, times(1)).send(DESTINATION_BINDING, notificacao);
    }
}