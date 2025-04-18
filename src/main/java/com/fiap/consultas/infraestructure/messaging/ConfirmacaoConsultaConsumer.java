package com.fiap.consultas.infraestructure.messaging;

import com.fiap.consultas.application.dtos.ConfirmacaoConsultaDTO;
import com.fiap.consultas.application.usecases.ReceberConfirmacaoConsultaUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfirmacaoConsultaConsumer {

    private final ReceberConfirmacaoConsultaUseCase receberConfirmacaoConsultaUseCase;

    @Bean
    public Consumer<Message<ConfirmacaoConsultaDTO>> receberConfirmacaoConsulta() {
        return message -> {
            try {
                ConfirmacaoConsultaDTO confirmacao = message.getPayload();
                log.info("Recebida confirmação para consulta: {}", confirmacao.getConsultaId());
                boolean executar = receberConfirmacaoConsultaUseCase.executar(confirmacao);
                log.info("Consulta recebeu confirmação com sucesso: %s".formatted(executar));
            } catch (Exception e) {
                log.error("Erro ao processar confirmação de consulta", e);
            }
        };
    }
}