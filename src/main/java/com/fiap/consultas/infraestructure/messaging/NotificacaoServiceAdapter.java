package com.fiap.consultas.infraestructure.messaging;

import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.application.ports.NotificacaoServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificacaoServiceAdapter implements NotificacaoServicePort {

    private final StreamBridge streamBridge;
    private static final String DESTINATION_BINDING = "enviarNotificacao-out-0";

    @Override
    public void enviarNotificacao(NotificacaoDTO notificacao) {
        boolean send = streamBridge.send(DESTINATION_BINDING, notificacao);
        System.out.println("enviado " + send);
    }
}
