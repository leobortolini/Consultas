package com.fiap.consultas.infraestructure.messaging;

import com.fiap.consultas.application.dtos.ConfirmacaoConsultaDTO;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.infraestructure.persistence.entities.ConsultaJpaEntity;
import com.fiap.consultas.infraestructure.persistence.repositories.ConsultaJpaRepository;
import org.aspectj.lang.annotation.After;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

@SpringBootTest
@EnableTestBinder
@AutoConfigureTestDatabase
class ConfirmacaoConsultaConsumerIT {

    @Autowired
    private InputDestination input;

    @Autowired
    private ConsultaJpaRepository consultaRepository;

    @AfterEach
    void clean() {
        consultaRepository.deleteAll();
    }

    @Test
    @Sql("/scripts/inserir-consulta-agendada.sql")
    void deveConfirmarConsultaAoReceberMensagemDeConfirmacao() {
        // Arrange - Supondo que o script SQL inseriu uma consulta com id conhecido
        String consultaId = "123e4567-e89b-12d3-a456-426614174000";

        ConfirmacaoConsultaDTO dto = ConfirmacaoConsultaDTO.builder()
                .consultaId(consultaId)
                .confirmada(true)
                .build();

        Message<ConfirmacaoConsultaDTO> message = MessageBuilder.withPayload(dto).build();

        // Act - Enviando a mensagem para o canal de entrada
        input.send(message, "receberConfirmacaoConsulta-in-0");

        // Assert - Verificando se a consulta foi confirmada no banco de dados
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<ConsultaJpaEntity> consultaAtualizada = consultaRepository.findById(UUID.fromString(consultaId));
            assertThat(consultaAtualizada).isPresent();
            assertThat(consultaAtualizada.get().getStatus()).isEqualTo(StatusConsulta.CONFIRMADA);
        });
    }

    @Test
    @Sql("/scripts/inserir-consulta-agendada.sql")
    void deveCancelarConsultaAoReceberMensagemDeNaoConfirmacao() {
        // Arrange - Supondo que o script SQL inseriu uma consulta com id conhecido
        String consultaId = "123e4567-e89b-12d3-a456-426614174000";

        ConfirmacaoConsultaDTO dto = ConfirmacaoConsultaDTO.builder()
                .consultaId(consultaId)
                .confirmada(false)
                .build();

        Message<ConfirmacaoConsultaDTO> message = MessageBuilder.withPayload(dto).build();

        // Act - Enviando a mensagem para o canal de entrada
        input.send(message, "receberConfirmacaoConsulta-in-0");

        // Assert - Verificando se a consulta foi cancelada no banco de dados
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<ConsultaJpaEntity> consultaAtualizada = consultaRepository.findById(UUID.fromString(consultaId));
            assertThat(consultaAtualizada).isPresent();
            assertThat(consultaAtualizada.get().getStatus()).isEqualTo(StatusConsulta.CANCELADA);
        });
    }

    @Test
    void deveManterIntegridadeDoBancoDadosAoReceberMensagemInvalida() {
        // Arrange - Criando uma mensagem com ID de consulta inexistente
        String consultaIdInexistente = "000e0000-e00b-00d0-a000-000000000000";

        ConfirmacaoConsultaDTO dto = ConfirmacaoConsultaDTO.builder()
                .consultaId(consultaIdInexistente)
                .confirmada(true)
                .build();

        Message<ConfirmacaoConsultaDTO> message = MessageBuilder.withPayload(dto).build();

        // Contando o número de registros antes do teste
        long countAntes = consultaRepository.count();

        // Act - Enviando a mensagem para o canal de entrada
        input.send(message, "receberConfirmacaoConsulta-in-0");

        // Assert - Verificamos que não houve mudança no banco de dados
        await().during(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Verificamos que o número de registros não mudou
            assertThat(consultaRepository.count()).isEqualTo(countAntes);

            // Verificamos que não existe consulta com o ID inexistente
            assertThat(consultaRepository.findById(UUID.fromString(consultaIdInexistente))).isEmpty();
        });
    }
}