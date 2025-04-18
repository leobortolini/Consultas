package com.fiap.consultas.infraestructure.messaging;

import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.application.ports.NotificacaoServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacaoServiceAdapter implements NotificacaoServicePort {

    private final StreamBridge streamBridge;
    private static final String DESTINATION_BINDING = "enviarNotificacao-out-0";

    @Override
    public void enviarNotificacao(NotificacaoDTO notificacao) {
        boolean send = streamBridge.send(DESTINATION_BINDING, notificacao);
        log.info("Notificacao enviada " + send);
    }
}
