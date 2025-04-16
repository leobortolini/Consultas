package com.fiap.consultas.infraestructure.api;

import com.fiap.consultas.application.dtos.RespostaAgendamentoDTO;
import com.fiap.consultas.application.dtos.SolicitacaoAgendamentoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
class AgendamentoControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void solicitarAgendamento_deveCriarConsultaComStatusPendenteAgendamento() throws Exception {
        // Arrange
        SolicitacaoAgendamentoDTO solicitacao = new SolicitacaoAgendamentoDTO();
        solicitacao.setCpfPaciente("12345678900");
        solicitacao.setEspecialidade("Cardiologia");
        solicitacao.setCidade("São Paulo");
        solicitacao.setPrioridade(PrioridadeConsulta.MEDIA);

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/api/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitacao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consultaId").exists())
                .andExpect(jsonPath("$.status").value(StatusConsulta.PENDENTE_AGENDAMENTO.toString()))
                .andExpect(jsonPath("$.mensagem").exists())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        RespostaAgendamentoDTO resposta = objectMapper.readValue(responseContent, RespostaAgendamentoDTO.class);

        assertNotNull(resposta.getConsultaId());
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, resposta.getStatus());
        assertNotNull(resposta.getMensagem());
    }

    @Test
    void solicitarAgendamento_comPrioridadeUrgente_deveCriarConsultaComStatusPendenteAgendamento() throws Exception {
        // Arrange
        SolicitacaoAgendamentoDTO solicitacao = new SolicitacaoAgendamentoDTO();
        solicitacao.setCpfPaciente("98765432100");
        solicitacao.setEspecialidade("Neurologia");
        solicitacao.setCidade("Rio de Janeiro");
        solicitacao.setPrioridade(PrioridadeConsulta.URGENTE);

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/api/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitacao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consultaId").exists())
                .andExpect(jsonPath("$.status").value(StatusConsulta.PENDENTE_AGENDAMENTO.toString()))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        RespostaAgendamentoDTO resposta = objectMapper.readValue(responseContent, RespostaAgendamentoDTO.class);

        assertNotNull(resposta.getConsultaId());
        assertEquals(StatusConsulta.PENDENTE_AGENDAMENTO, resposta.getStatus());
    }

    @Test
    void solicitarAgendamento_paraEspecialidadesDiferentes_deveGerarIdsUnicosDiferentes() throws Exception {
        // Arrange - Primeira solicitação
        SolicitacaoAgendamentoDTO solicitacao1 = new SolicitacaoAgendamentoDTO();
        solicitacao1.setCpfPaciente("12345678900");
        solicitacao1.setEspecialidade("Cardiologia");
        solicitacao1.setCidade("São Paulo");
        solicitacao1.setPrioridade(PrioridadeConsulta.MEDIA);

        // Arrange - Segunda solicitação
        SolicitacaoAgendamentoDTO solicitacao2 = new SolicitacaoAgendamentoDTO();
        solicitacao2.setCpfPaciente("12345678900");
        solicitacao2.setEspecialidade("Oftalmologia");
        solicitacao2.setCidade("São Paulo");
        solicitacao2.setPrioridade(PrioridadeConsulta.BAIXA);

        // Act - Primeira solicitação
        MvcResult result1 = mockMvc.perform(post("/api/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitacao1)))
                .andExpect(status().isCreated())
                .andReturn();

        // Act - Segunda solicitação
        MvcResult result2 = mockMvc.perform(post("/api/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitacao2)))
                .andExpect(status().isCreated())
                .andReturn();

        // Assert - Extrair IDs das consultas
        RespostaAgendamentoDTO resposta1 = objectMapper.readValue(
                result1.getResponse().getContentAsString(), RespostaAgendamentoDTO.class);
        RespostaAgendamentoDTO resposta2 = objectMapper.readValue(
                result2.getResponse().getContentAsString(), RespostaAgendamentoDTO.class);

        // Assert - Verificar que os IDs são diferentes
        assertNotNull(resposta1.getConsultaId());
        assertNotNull(resposta2.getConsultaId());

        // Verificar que os UUIDs são diferentes
        // Usar uma comparação direta para verificar que não são iguais
        assertNotEquals(resposta1.getConsultaId(), resposta2.getConsultaId());
    }

}