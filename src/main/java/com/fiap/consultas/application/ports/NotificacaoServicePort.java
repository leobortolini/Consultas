package com.fiap.consultas.application.ports;

import com.fiap.consultas.application.dtos.NotificacaoDTO;

public interface NotificacaoServicePort {
    void enviarNotificacao(NotificacaoDTO notificacao);
}
